package mil.tron.commonapi.integration;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.findify.s3mock.S3Mock;
import mil.tron.commonapi.JwtUtils;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceInfoDto;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = { "security.enabled=true",
        "efa-enabled=false",
        "minio.enabled=true",
        "minio.connection-string=http://localhost:9002",
        "minio.access-key=admin",
        "minio.secret-key=admin",
        "minio.bucket-name=testbucket",
        "aws-default-region=EARTH"
})
@ActiveProfiles(value = { "development", "test" })  // enable at least dev so we get tracing enabled for full integration
@AutoConfigureMockMvc
public class DocumentSpaceIntegrationTests {

    public static final String ENDPOINT_V2 = "/v2/document-space";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    MockMvc mockMvc;

    private AmazonS3 amazonS3;
    private S3Mock s3Mock;

    @Autowired
    AppClientUserRespository appClientUserRespository;

    @Autowired
    PrivilegeRepository privRepo;

    @Autowired
    private DashboardUserRepository dashRepo;
    private DashboardUser admin;

    UUID id = UUID.randomUUID();

    @BeforeEach
    void setup() {
        // create the admin
        admin = DashboardUser.builder()
                .id(id)
                .email("admin@commonapi.net")
                .privileges(Set.of(
                        privRepo.findByName("DASHBOARD_ADMIN").orElseThrow(() -> new RecordNotFoundException("No DASH_BOARD ADMIN")),
                        privRepo.findByName("DASHBOARD_USER").orElseThrow(() -> new RecordNotFoundException("No DASH_BOARD USER"))
                ))
                .build();

        // persist the admin
        if (!dashRepo.existsById(id))  dashRepo.save(admin);

        BasicAWSCredentials awsCredentials = new BasicAWSCredentials("admin", "admin");
        ClientConfiguration clientConfiguration = new ClientConfiguration();

        amazonS3 = AmazonS3ClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("http://localhost:9002", "Earth"))
                .withPathStyleAccessEnabled(true)
                .withClientConfiguration(clientConfiguration)
                .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
                .build();

        s3Mock = new S3Mock.Builder()
                .withPort(9002)
                .withInMemoryBackend()
                .build();

        s3Mock.start();
        amazonS3.createBucket("testbucket");
    }

    @AfterEach
    void destroy() {
        s3Mock.shutdown();
        dashRepo.deleteById(id);
    }

    @Test
    void testSpaceCreation() throws Exception {

        // check no spaces
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces")
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        // create space
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceInfoDto
                        .builder()
                        .name("test1")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", equalTo("test1")));

        // check 1 space
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces")
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        // check no dupes
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceInfoDto
                        .builder()
                        .name("test1")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isConflict());

        // check invalid name(s)
        for (String name : new String[] { "  ", "", null, ".folder", "Uppercase", "Slashed/Name", "Under_score" }) {
            mockMvc.perform(post(ENDPOINT_V2 + "/spaces")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(MAPPER.writeValueAsString(DocumentSpaceInfoDto
                            .builder()
                            .name(name)
                            .build()))
                    .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                    .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                    .andExpect(status().isBadRequest());
        }

        // create many spaces
        for (String name : new String[] { "test0", "cool.space", "test2", "test3" }) {
            mockMvc.perform(post(ENDPOINT_V2 + "/spaces")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(MAPPER.writeValueAsString(DocumentSpaceInfoDto
                            .builder()
                            .name(name)
                            .build()))
                    .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                    .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                    .andExpect(status().isCreated());
        }

        // verify 5 spaces
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces")
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(5)));

        // update file to space
        MockMultipartFile file
                = new MockMultipartFile(
                "file",
                "hello.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Hello, World!".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/files/test0").file(file)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // check file in space test0
        mockMvc.perform(get(ENDPOINT_V2 + "/files/test0")
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].key", equalTo("hello.txt")));

        // check file isn't in space test0
        mockMvc.perform(get(ENDPOINT_V2 + "/files/test1")
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // upload same file again
        mockMvc.perform(get(ENDPOINT_V2 + "/files/test0")
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].key", equalTo("hello.txt")));

        // delete invalid file
        mockMvc.perform(delete(ENDPOINT_V2 + "/files/test0/test.txt")
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isNotFound());

        // delete valid file
        mockMvc.perform(delete(ENDPOINT_V2 + "/files/test0/hello.txt")
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isNoContent());


    }

    @Test
    void testSpaceDeletion() throws Exception {
        // create space
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceInfoDto
                        .builder()
                        .name("test1")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", equalTo("test1")));

        // check 1 space
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces")
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        // delete space
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/test1")
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isNoContent());

        // delete space - that doesnt exist
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/test1")
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isNotFound());
    }

}
