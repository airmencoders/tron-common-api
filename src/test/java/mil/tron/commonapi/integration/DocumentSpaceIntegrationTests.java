package mil.tron.commonapi.integration;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.findify.s3mock.S3Mock;
import mil.tron.commonapi.JwtUtils;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceCreateFolderDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpacePathDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceRequestDto;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceFileSystemEntryRepository;
import mil.tron.commonapi.repository.documentspace.DocumentSpacePrivilegeRepository;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceRepository;
import mil.tron.commonapi.service.documentspace.DocumentSpaceFileSystemService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.transaction.Transactional;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
@AutoConfigureTestDatabase
public class DocumentSpaceIntegrationTests {

    public static final String ENDPOINT_V2 = "/v2/document-space";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    MockMvc mockMvc;

    private AmazonS3 amazonS3;
    private S3Mock s3Mock;

    @Autowired
    private DocumentSpaceRepository documentSpaceRepository;
    
    @Autowired
    private DocumentSpacePrivilegeRepository documentSpacePrivilegeRepository;

    @Autowired
    private DocumentSpaceFileSystemEntryRepository fileSystemEntryRepository;

    @Autowired
    AppClientUserRespository appClientUserRespository;

    @Autowired
    DocumentSpaceFileSystemService fileSystemService;

    @Autowired
    PrivilegeRepository privRepo;

    @Autowired
    private DashboardUserRepository dashRepo;
    private DashboardUser admin;

    UUID id = UUID.randomUUID();

    @BeforeEach
    void setup() {
        fileSystemEntryRepository.deleteAll();

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
        documentSpacePrivilegeRepository.deleteAll();
        documentSpaceRepository.deleteAll();
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
        MvcResult result = mockMvc.perform(post(ENDPOINT_V2 + "/spaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceRequestDto
                        .builder()
                        .name("test1")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", equalTo("test1")))
                .andReturn();
        
        UUID test1Id = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));

        // check 1 space
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces")
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andReturn();
        
        // check that document spaces cannot be created with duplicated names
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceRequestDto
                        .builder()
                        .name("test1")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isConflict());

        // create many spaces
        Map<String, UUID> spaces = new HashMap<>();
        for (String name : new String[] { "test0", "cool.space", "test2", "test3" }) {
        	result = mockMvc.perform(post(ENDPOINT_V2 + "/spaces")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(MAPPER.writeValueAsString(DocumentSpaceRequestDto
                            .builder()
                            .name(name)
                            .build()))
                    .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                    .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                    .andExpect(status().isCreated())
                    .andReturn();
        	
        	spaces.put(name, UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id")));
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
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload", test1Id.toString()).file(file)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // check file in space test1
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/files", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(1)))
                .andExpect(jsonPath("$.documents[0].key", equalTo("hello.txt")));

        // check file isn't in space test0
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/files", spaces.get("test0").toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(0)));

        // upload same file again to test1
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/files", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(1)))
                .andExpect(jsonPath("$.documents[0].key", equalTo("hello.txt")));

        // delete invalid file
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}/files/delete?file=doesnotexistfile.txt", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isNotFound());

        // delete valid file
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}/files/delete?file=hello.txt", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isNoContent());
    }

	@Test
	void testSpaceDeletion() throws Exception {
		// create space
		mockMvc.perform(post(ENDPOINT_V2 + "/spaces").contentType(MediaType.APPLICATION_JSON)
				.content(MAPPER.writeValueAsString(DocumentSpaceRequestDto.builder().name("test1").build()))
				.header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
				.header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.name", equalTo("test1")));

		// check 1 space
		MvcResult result = mockMvc
				.perform(get(ENDPOINT_V2 + "/spaces")
						.header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
						.header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(1))).andReturn();

		// delete space
		mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}", UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.data[0].id")).toString())
				.header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
				.header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
				.andExpect(status().isNoContent());

		// delete space - that doesnt exist
		mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}", UUID.randomUUID().toString())
				.header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
				.header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
				.andExpect(status().isNotFound());
	}

	@Transactional
    @Rollback
	@Test
    void testFolderAndFileSystemOps() throws Exception {

        // check no spaces
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces")
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        // create space
        MvcResult result = mockMvc.perform(post(ENDPOINT_V2 + "/spaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceRequestDto
                        .builder()
                        .name("test1")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", equalTo("test1")))
                .andReturn();

        UUID test1Id = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));

        // make folder "docs"
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/folders", test1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceCreateFolderDto.builder()
                        .folderName("docs")
                        .path("/")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullPathSpec", equalTo("docs")));

        // update file to space
        MockMultipartFile file
                = new MockMultipartFile(
                "file",
                "hello.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Hello, World!".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload", test1Id.toString()).file(file)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // check file in space test1
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files", hasSize(1)))
                .andExpect(jsonPath("$.subFolderElements", hasSize(1)))
                .andExpect(jsonPath("$.files[0]", equalTo("hello.txt")))
                .andExpect(jsonPath("$.subFolderElements[0].itemName", equalTo("docs")));

        // checks root without the path param
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files", hasSize(1)))
                .andExpect(jsonPath("$.subFolderElements", hasSize(1)))
                .andExpect(jsonPath("$.files[0]", equalTo("hello.txt")))
                .andExpect(jsonPath("$.subFolderElements[0].itemName", equalTo("docs")));

        // try make duplicate folder name
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/folders", test1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceCreateFolderDto.builder()
                        .folderName("docs")
                        .path("/")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isConflict());

        // make new folder within docs named "notes"
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/folders", test1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceCreateFolderDto.builder()
                        .folderName("notes")
                        .path("/docs")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullPathSpec", equalTo("docs/notes")));

        // from root level - see we still have same as before (in the eyes of the root)
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files", hasSize(1)))
                .andExpect(jsonPath("$.subFolderElements", hasSize(1)))
                .andExpect(jsonPath("$.files[0]", equalTo("hello.txt")))
                .andExpect(jsonPath("$.subFolderElements[0].itemName", equalTo("docs")));

        // from docs level - see we have one subfolder no files
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/docs", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files", hasSize(0)))
                .andExpect(jsonPath("$.subFolderElements", hasSize(1)))
                .andExpect(jsonPath("$.subFolderElements[0].itemName", equalTo("notes")));

        // add file to docs/notes
        MockMultipartFile notesFile
                = new MockMultipartFile(
                "file",
                "notes.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Notes!!".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=/docs/notes", test1Id.toString()).file(notesFile)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // check file is there now /docs/notes/notes.txt
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/docs/notes", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files", hasSize(1)))
                .andExpect(jsonPath("$.subFolderElements", hasSize(0)))
                .andExpect(jsonPath("$.files[0]", equalTo("notes.txt")));

        // add another file to docs/notes
        MockMultipartFile accountingFile
                = new MockMultipartFile(
                "file",
                "accounts.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Money!!".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=/docs/notes", test1Id.toString()).file(accountingFile)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // trust but verify...
        // check file is there now /docs/notes
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/docs/notes", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files", hasSize(2)))
                .andExpect(jsonPath("$.subFolderElements", hasSize(0)))
                .andExpect(jsonPath("$.files", Matchers.hasItem("accounts.txt")));

        // now just delete one file
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}/files/delete?path=/docs/notes&file=accounts.txt", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isNoContent());

        // now test delete again - should be 404
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}/files/delete?path=/docs/notes&file=accounts.txt", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isNotFound());

        // check accounting.txt file gone
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/docs/notes", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files", hasSize(1)))
                .andExpect(jsonPath("$.subFolderElements", hasSize(0)))
                .andExpect(jsonPath("$.files", not(Matchers.hasItem("accounts.txt"))));

        // dump ALL files - from this space- ignore folders for the moment from the doc space
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/files", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)));

        // now delete /docs (and everything underneath it)
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}/folders", test1Id.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpacePathDto.builder()
                        .path("/docs")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isNoContent());

        // make sure files and folders are gone, except for hello.txt
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/files", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(1)));

        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files", hasSize(1)))
                .andExpect(jsonPath("$.subFolderElements", hasSize(0)))
                .andExpect(jsonPath("$.files[0]", equalTo("hello.txt")));

        // make a new folder "jobs/name" - but with / in the new name - should error out 400
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/folders", test1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceCreateFolderDto.builder()
                        .folderName("jobs/name")
                        .path("/docs")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isBadRequest());

        // try to upload a filename with a / - should 400
        MockMultipartFile invalidFile
                = new MockMultipartFile(
                "file",
                "new/file.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Invalid!!".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload", test1Id.toString()).file(invalidFile)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isBadRequest());
    }

    @Transactional
    @Rollback
    @Test
    void testGetFilesAndFolders() throws Exception {

        // create space
        MvcResult result = mockMvc.perform(post(ENDPOINT_V2 + "/spaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceRequestDto
                        .builder()
                        .name("test1")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", equalTo("test1")))
                .andReturn();

        UUID test1Id = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));

        // make folder "docs"
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/folders", test1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceCreateFolderDto.builder()
                        .folderName("docs")
                        .path("/")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullPathSpec", equalTo("docs")));

        // upload file to space
        MockMultipartFile file
                = new MockMultipartFile(
                "file",
                "hello.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Hello, World!".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload", test1Id.toString())
                .file(file)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // upload file to docs folder
        MockMultipartFile file2
                = new MockMultipartFile(
                "file",
                "hello2.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Hello, World 2!".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=/docs", test1Id.toString())
                .file(file2)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());


        // now get the file from "/docs/hello2.txt" folder
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/files/download/single?path=/docs&file=hello2.txt", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hello, World 2!")));

        // make new folder within /docs named "lists"
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/folders", test1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceCreateFolderDto.builder()
                        .folderName("lists")
                        .path("/docs")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullPathSpec", equalTo("docs/lists")));

        // add a file "lists.txt" to the new folder
        MockMultipartFile lists
                = new MockMultipartFile(
                "file",
                "lists.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "1. Stuff".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=/docs/lists", test1Id.toString())
                .file(lists)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // upload a new file to the root of the space
        MockMultipartFile file3
                = new MockMultipartFile(
                "file",
                "hello3.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Hello, World 3!".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=/", test1Id.toString())
                .file(file3)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // so now we should have something like this in the doc space--
        //
        // / <root>
        // |- docs/
        // |  |- lists/
        // |  |  |- lists.txt
        // |  |- hello2.txt
        // |- hello.txt
        // |- hello3.txt
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/files", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(4)));

        // download and zip all the files from /docs which should just be one file and the folder "lists" with its one file


    }


}
