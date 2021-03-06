package mil.tron.commonapi.integration;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.jayway.jsonpath.JsonPath;
import io.findify.s3mock.S3Mock;
import mil.tron.commonapi.JwtUtils;
import mil.tron.commonapi.dto.PrivilegeDto;
import mil.tron.commonapi.dto.ScratchStorageAppUserPrivDto;
import mil.tron.commonapi.dto.ScratchStorageUserDto;
import mil.tron.commonapi.dto.appclient.AppClientUserDto;
import mil.tron.commonapi.dto.documentspace.*;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.documentspace.DocumentSpace;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceFileSystemEntryRepository;
import mil.tron.commonapi.repository.documentspace.DocumentSpacePrivilegeRepository;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceRepository;
import mil.tron.commonapi.service.documentspace.DocumentSpaceFileSystemService;
import mil.tron.commonapi.service.documentspace.DocumentSpaceService;
import mil.tron.commonapi.service.documentspace.util.FilePathSpecWithContents;
import org.apache.commons.io.FileUtils;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import javax.transaction.Transactional;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = { "security.enabled=true",
        "efa-enabled=false",
        "minio.enabled=true",
        "minio.connection-string=http://localhost:9005",
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
    public static final String APP_CLIENT_ENDPOINT_V2 = "/v2/app-client";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    MockMvc mockMvc;

    private AmazonS3 amazonS3;
    private S3Mock s3Mock;

    @Autowired
    private DocumentSpaceRepository documentSpaceRepository;

    @Autowired
    private DocumentSpaceService documentSpaceService;
    
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
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("http://localhost:9005", "Earth"))
                .withPathStyleAccessEnabled(true)
                .withClientConfiguration(clientConfiguration)
                .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
                .build();

        s3Mock = new S3Mock.Builder()
                .withPort(9005)
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

        // delete invalid/non-existent file
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}/delete", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpacePathItemsDto.builder()
                    .currentPath("")
                    .items(Lists.newArrayList("doesnotexistfile.txt"))
                    .build())))
                .andExpect(status().isNotFound());

        // delete valid file
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}/delete", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpacePathItemsDto.builder()
                        .currentPath("")
                        .items(Lists.newArrayList("hello.txt"))
                        .build())))
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
                .andExpect(jsonPath("$.documents[*].key", hasSize(2)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("hello.txt")))
                .andExpect(jsonPath("$.documents[*].key", hasItem("docs")));

        // checks root without the path param
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[*].key", hasSize(2)))
                .andExpect(jsonPath("$.documents[?(@.folder == true)]", hasSize(1)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("hello.txt")))
                .andExpect(jsonPath("$.documents[*].key", hasItem("docs")));

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
                .andExpect(jsonPath("$.documents", hasSize(2)))
                .andExpect(jsonPath("$.documents[?(@.folder == true)]", hasSize(1)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("hello.txt")))
                .andExpect(jsonPath("$.documents[*].key", hasItem("docs")));

        // from docs level - see we have one subfolder no files
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/docs", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(1)))
                .andExpect(jsonPath("$.documents[?(@.folder == true)]", hasSize(1)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("notes")));

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
                .andExpect(jsonPath("$.documents", hasSize(1)))
                .andExpect(jsonPath("$.documents[?(@.folder == true)]", hasSize(0)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("notes.txt")));

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
                .andExpect(jsonPath("$.documents", hasSize(2)))
                .andExpect(jsonPath("$.documents[?(@.folder == true)]", hasSize(0)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("accounts.txt")));

        // now just delete one file
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}/delete", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpacePathItemsDto.builder()
                        .currentPath("/docs/notes")
                        .items(Lists.newArrayList("accounts.txt"))
                        .build())))
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
                .andExpect(jsonPath("$.documents", hasSize(1)))
                .andExpect(jsonPath("$.documents[?(@.folder == true)]", hasSize(0)))
                .andExpect(jsonPath("$.documents[*].key", not(Matchers.hasItem("accounts.txt"))));

        // dump ALL files - from this space- ignore folders for the moment from the doc space
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/files", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)));

        // now delete /docs (and everything underneath it)
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}/delete", test1Id.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpacePathItemsDto.builder()
                        .currentPath("/")
                        .items(Lists.newArrayList("docs"))
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
                .andExpect(jsonPath("$.documents", hasSize(1)))
                .andExpect(jsonPath("$.documents[?(@.folder == true)]", hasSize(0)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("hello.txt")));

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

        // try to upload a filename with a / - should 200 because we can create folders on-the-fly because of folder upload
        MockMultipartFile validFile
                = new MockMultipartFile(
                "file",
                "new/file.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Invalid!!".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload", test1Id.toString()).file(validFile)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // but this should fail
        MockMultipartFile invalidFile
                = new MockMultipartFile(
                "file",
                "new/fil..e.txt",
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
        MockMultipartFile names
                = new MockMultipartFile(
                "file",
                "names.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "names".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=/docs", test1Id.toString())
                .file(file2)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=/docs", test1Id.toString())
                .file(names)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());


        // now get the file from "/docs/hello2.txt" folder using semantic path addressing
        mockMvc.perform(get(ENDPOINT_V2 + "/space/{id}/docs/hello2.txt", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hello, World 2!")));

        mockMvc.perform(get(ENDPOINT_V2 + "/space/{id}/docs/", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(ENDPOINT_V2 + "/space/{id}/hello.txt", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hello, World!")));

        mockMvc.perform(get(ENDPOINT_V2 + "/space/{id}/docs", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().isNotFound());

        // upload a file with no extension to /docs and download it
        MockMultipartFile noExt
                = new MockMultipartFile(
                "file",
                "words",
                MediaType.TEXT_PLAIN_VALUE,
                "words".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=/docs", test1Id.toString())
                .file(noExt)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());
        mockMvc.perform(get(ENDPOINT_V2 + "/space/{id}/docs/words", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("words")));


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
        // |  |- names.txt
        // |- hello.txt
        // |- hello3.txt
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/files", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(6)));

        // download and zip all the files from /docs which should just be one file and the folder "lists" with its one file
        // should have structure like this in the zip file
        // |- docs/
        // |  |- lists/
        // |  |  |- lists.txt
        // |  |- hello2.txt
        String tmpdir = Files.createTempDir().getAbsolutePath();
        File zipFile = new File(tmpdir + File.separator + "files.zip");
        FileOutputStream fos = new FileOutputStream(zipFile);
        documentSpaceService.downloadAndWriteCompressedFiles(test1Id, "/docs", Set.of("hello2.txt", "lists"), fos, admin.getEmail());
        fos.close();

        ZipFile zf = new ZipFile(zipFile);
        Enumeration<? extends ZipEntry> entries = zf.entries();
        List<String> contents = new ArrayList<>();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            contents.add(entry.getName());
        }
        zf.close();
        FileUtils.deleteDirectory(new File(tmpdir));
        assertTrue(contents.contains("docs/hello2.txt"));
        assertTrue(contents.contains("docs/lists/lists.txt"));

        // test downloading all by selection
        // should have this structure:
        // / <root>
        // |- docs/
        // |  |- lists/
        // |  |  |- lists.txt
        // |  |- hello2.txt
        // |  |- names.txt
        // |- hello.txt
        // |- hello3.txt
        tmpdir = Files.createTempDir().getAbsolutePath();
        zipFile = new File(tmpdir + File.separator + "files.zip");
        fos = new FileOutputStream(zipFile);
        documentSpaceService.downloadAndWriteCompressedFiles(test1Id, "/", Set.of("hello.txt", "hello3.txt", "docs"), fos, admin.getEmail());
        fos.close();

        zf = new ZipFile(zipFile);
        entries = zf.entries();
        contents = new ArrayList<>();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            contents.add(entry.getName());
        }
        zf.close();
        FileUtils.deleteDirectory(new File(tmpdir));
        assertTrue(contents.contains("hello.txt"));
        assertTrue(contents.contains("hello3.txt"));
        assertTrue(contents.contains("docs/lists/lists.txt"));
        assertTrue(contents.contains("docs/hello2.txt"));
        assertTrue(contents.contains("docs/words"));
        assertTrue(contents.contains("docs/names.txt"));

        // test downloading the whole space - check directory integrity
        // should have this structure:
        // / <root>
        // |- docs/
        // |  |- lists/
        // |  |  |- lists.txt
        // |  |- hello2.txt
        // |  |- names.txt
        // |- hello.txt
        // |- hello3.txt
        tmpdir = Files.createTempDir().getAbsolutePath();
        zipFile = new File(tmpdir + File.separator + "files.zip");
        fos = new FileOutputStream(zipFile);
        documentSpaceService.downloadAllInSpaceAndCompress(test1Id, fos);
        fos.close();

        zf = new ZipFile(zipFile);
        entries = zf.entries();
        contents = new ArrayList<>();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            contents.add(entry.getName());
        }
        zf.close();
        FileUtils.deleteDirectory(new File(tmpdir));
        assertTrue(contents.contains("hello.txt"));
        assertTrue(contents.contains("hello3.txt"));
        assertTrue(contents.contains("docs/lists/lists.txt"));
        assertTrue(contents.contains("docs/hello2.txt"));
        assertTrue(contents.contains("docs/names.txt"));
        assertTrue(contents.contains("docs/words"));

        // rename "/docs" to "records"
        assertThrows(ResourceAlreadyExistsException.class, () -> documentSpaceService.renameFolder(test1Id, "/docs", "docs"));
        documentSpaceService.renameFolder(test1Id, "/docs", "records");
        tmpdir = Files.createTempDir().getAbsolutePath();
        zipFile = new File(tmpdir + File.separator + "files.zip");
        fos = new FileOutputStream(zipFile);
        documentSpaceService.downloadAndWriteCompressedFiles(test1Id, "/records", Set.of("hello2.txt", "lists"), fos, admin.getEmail());
        fos.close();

        zf = new ZipFile(zipFile);
        entries = zf.entries();
        contents = new ArrayList<>();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            contents.add(entry.getName());
        }
        zf.close();
        FileUtils.deleteDirectory(new File(tmpdir));
        assertTrue(contents.contains("records/hello2.txt"));
        assertTrue(contents.contains("records/lists/lists.txt"));

        // now let's delete things, remember we have this structure at the moment
        // / <root>
        // |- records/
        // |  |- lists/
        // |  |  |- lists.txt
        // |  |- hello2.txt
        // |  |- names.txt
        // |- hello.txt
        // |- hello3.txt
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}/delete", test1Id)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpacePathItemsDto.builder()
                        .currentPath("/records")
                        .items(Lists.newArrayList("lists", "hello2.txt"))
                        .build())))
                .andExpect(status().isNoContent());


        // check we still have /docs/names.txt
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/records", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)))
                .andExpect(jsonPath("$.documents[?(@.folder == true)]", hasSize(0)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("names.txt")))
                .andExpect(jsonPath("$.documents[*].key", hasItem("words")));

        // re-issuing this should fail due to files not existing
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}/delete", test1Id)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpacePathItemsDto.builder()
                        .currentPath("/records")
                        .items(Lists.newArrayList("lists", "hello2.txt"))
                        .build())))
                .andExpect(status().isNotFound());

    }

    @Transactional
    @Rollback
    @Test
    void testArchiveFunctions() throws Exception {
        UUID test1Id = createSpaceWithFiles("test1");
        FilePathSpecWithContents contents = documentSpaceService.getFolderContents(test1Id, "/docs");
        DocumentSpaceFileSystemEntry namesItem = contents.getEntries().stream().filter(item -> item.getItemName().equals("names.txt")).findFirst().get();

        // should have this structure ready for us
        // / <root>
        // |- docs/
        // |  |- hello2.txt
        // |  |- names.txt
        // |- hello.txt

        // now archive the /docs/names.txt
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}/archive", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceArchiveItemsDto.builder()
                        .currentPath("/docs")
                        .itemsToArchive(Lists.newArrayList("names.txt"))
                        .build())))
                .andExpect(status().isNoContent());

        // confirm archived status in the database
        assertTrue(fileSystemEntryRepository.findByItemIdEquals(namesItem.getItemId()).get().isDeleteArchived());

        // confirm via controller
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/docs", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(1)));  // confirm just 1 element(s) left in the docs folder

        // browse archived - should be the one file
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/archived/contents", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(1)))  // has names.txt in it
                .andExpect(jsonPath("$.documents[0].key", equalTo("names.txt")));

        // unarchive it
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/unarchive", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceUnArchiveItemsDto.builder()
                        .itemsToUnArchive(Lists.newArrayList("/docs/names.txt"))
                        .build())))
                .andExpect(status().isNoContent());

        // confirm status is no longer archived
        assertFalse(fileSystemEntryRepository.findByItemIdEquals(namesItem.getItemId()).get().isDeleteArchived());

        // browse archived - should be nothing
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/archived/contents", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(0)));

        // two files should now be back in the /docs folder
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/docs", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)));

        // archive two files in the the docs folder
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}/archive", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceArchiveItemsDto.builder()
                        .currentPath("/docs")
                        .itemsToArchive(Lists.newArrayList("names.txt", "hello2.txt"))
                        .build())))
                .andExpect(status().isNoContent());

        // unarchive just the names.txt
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/unarchive", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceUnArchiveItemsDto.builder()
                        .itemsToUnArchive(Lists.newArrayList("/docs/names.txt"))
                        .build())))
                .andExpect(status().isNoContent());

        // should just be the one file left in the archived state
        // browse archived - should be nothing
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/archived/contents", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(1)));

        // unarchive hello2.txt
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/unarchive", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceUnArchiveItemsDto.builder()
                        .itemsToUnArchive(Lists.newArrayList("/docs/hello2.txt"))
                        .build())))
                .andExpect(status().isNoContent());

        // now archive (soft-delete) a whole folder ("/docs"), check it and its children are archived - be it sub-folders or files
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}/archive", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceArchiveItemsDto.builder()
                        .currentPath("/")
                        .itemsToArchive(Lists.newArrayList("docs"))
                        .build())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(1)));  // confirm just 1 element(s) left in the root folder

        // try to nav to path "/docs" shouldn't work since its archived
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/docs", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isNotFound());

        // look in the archived docs - at root level
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/archived/contents", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(1))) // one folder, "/docs"
                .andExpect(jsonPath("$.documents[0].key", equalTo("docs")));


        // since docs is archived - make a folder named docs in the non-archived space - this is currently NOT allowed
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/folders", test1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceCreateFolderDto.builder()
                        .folderName("docs")
                        .path("/")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isConflict());

        // unarchive the docs folder
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/unarchive", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceUnArchiveItemsDto.builder()
                        .itemsToUnArchive(Lists.newArrayList("docs"))
                        .build())))
                .andExpect(status().isNoContent());

        // confirm presence back in our space
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/archived/contents", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(0)));  // has nothing in it

        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)));

        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/docs", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)));

        // now that every is back to starting point, lets archive and hard-delete from archived-status

        // archive the /docs/names.txt
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}/archive", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceArchiveItemsDto.builder()
                        .currentPath("/docs")
                        .itemsToArchive(Lists.newArrayList("names.txt"))
                        .build())))
                .andExpect(status().isNoContent());

        // confirm archived status in the database
        assertTrue(fileSystemEntryRepository.findByItemIdEquals(namesItem.getItemId()).get().isDeleteArchived());

        // confirm via controller - its gone
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/archived/contents", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(1)))
                .andExpect(jsonPath("$.documents[0].path", equalTo("/docs")));

        // hard-delete from archived
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}/delete", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpacePathItemsDto.builder()
                        .currentPath("/docs")
                        .items(Lists.newArrayList("names.txt"))
                        .build())))
                .andExpect(status().isNoContent());

        // confirm via controller - its gone
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/archived/contents", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(0)));  // confirm just 1 element(s) left in the docs folder
    }

    @Transactional
    @Rollback
    @Test
    void testArchivingSameNamedElements() throws Exception {
        UUID test1Id = createSpaceWithFiles("space");

        // now archive the /docs/names.txt
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}/archive", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceArchiveItemsDto.builder()
                        .currentPath("/docs")
                        .itemsToArchive(Lists.newArrayList("names.txt"))
                        .build())))
                .andExpect(status().isNoContent());

        // upload a new names.txt to the /docs folder with old names.txt in the archived state - should NOT be allowed currently
        MockMultipartFile newNames
                = new MockMultipartFile(
                "file",
                "names.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "I'm the new names.txt".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=/docs", test1Id.toString())
                .file(newNames)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isConflict());

        // delete the archived copy
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}/delete", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpacePathItemsDto.builder()
                        .currentPath("/docs")
                        .items(Lists.newArrayList("names.txt"))
                        .build())))
                .andExpect(status().isNoContent());

        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=/docs", test1Id.toString())
                .file(newNames)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());
    }

    @Transactional
    @Rollback
    @Test
    void testGetArchivedItemsMultipleSpaces() throws Exception {

        // create 3 identical spaces
        UUID test2Id = createSpaceWithFiles("test2");
        UUID test3Id = createSpaceWithFiles("test3");
        UUID test4Id = createSpaceWithFiles("test4");

        DocumentSpace test2 = documentSpaceRepository.findById(test2Id).get();
        DocumentSpace test3 = documentSpaceRepository.findById(test3Id).get();
        DocumentSpace test4 = documentSpaceRepository.findById(test4Id).get();

        // associate user with just test2 and test3 (read-only on test2, and write on test3)
        DashboardUser someUser = DashboardUser.builder()
                .email("someUser@test.gov")
                .privileges(Set.of(
                        privRepo.findByName("DOCUMENT_SPACE_USER").orElseThrow(() -> new RecordNotFoundException("No Document Space User Priv")),
                        privRepo.findByName("DASHBOARD_USER").orElseThrow(() -> new RecordNotFoundException("No DASH_BOARD USER"))
                ))
                .build();

        dashRepo.save(someUser);
        documentSpaceService.addDashboardUserToDocumentSpace(test2Id, DocumentSpaceDashboardMemberRequestDto.builder()
                .email("someUser@test.gov")
                .privileges(Lists.newArrayList())
                .build());
        documentSpaceService.addDashboardUserToDocumentSpace(test3Id, DocumentSpaceDashboardMemberRequestDto.builder()
                .email("someUser@test.gov")
                .privileges(Lists.newArrayList(ExternalDocumentSpacePrivilegeType.WRITE))
                .build());

        // as some-user, archive the names.txt file from test3's docs/ folder
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}/archive", test3Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(someUser.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceArchiveItemsDto.builder()
                        .currentPath("/docs")
                        .itemsToArchive(Lists.newArrayList("names.txt"))
                        .build())))
                .andExpect(status().isNoContent());

        // as admin, archive from test2's docs/ folder, hello2.txt
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}/archive", test2Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceArchiveItemsDto.builder()
                        .currentPath("/docs")
                        .itemsToArchive(Lists.newArrayList("hello2.txt"))
                        .build())))
                .andExpect(status().isNoContent());

        // as admin, archive from test4's docs/ folder, hello2.txt
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}/archive", test4Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceArchiveItemsDto.builder()
                        .currentPath("/docs")
                        .itemsToArchive(Lists.newArrayList("hello2.txt"))
                        .build())))
                .andExpect(status().isNoContent());

        // as admin verify we have two items for viewing all archived items
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/archived")
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(3)))
                .andExpect(jsonPath("$.documents[?(@.spaceName == 'test2')]").exists())
                .andExpect(jsonPath("$.documents[?(@.spaceName == 'test3')]").exists())
                .andExpect(jsonPath("$.documents[?(@.spaceName == 'test4')]").exists());

        // as some-user, verify can see just two items - since we can't see into test4 space
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/archived")
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(someUser.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)))
                .andExpect(jsonPath("$.documents[?(@.spaceName == 'test2')]").exists())
                .andExpect(jsonPath("$.documents[?(@.spaceName == 'test3')]").exists());

        // as some-user, make sure we can restore test3's file to test3 space
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/unarchive", test3Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(someUser.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceUnArchiveItemsDto.builder()
                        .itemsToUnArchive(Lists.newArrayList("/docs/names.txt"))
                        .build())))
                .andExpect(status().isNoContent());

        // one item left in archived
        // as some-user, verify can see just two items - since we can't see into test4 space
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/archived")
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(someUser.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(1)))
                .andExpect(jsonPath("$.documents[?(@.spaceName == 'test2')]").exists());
    }

    @Transactional
    @Rollback
    @Test
    void testRenameFileOps() throws Exception {
        UUID spaceId = createSpaceWithFiles("some-space");

        // should have this structure ready for us
        // / <root>
        // |- docs/
        // |  |- hello2.txt
        // |  |- names.txt
        // |- hello.txt

        // rename hello.txt to itself (allowed)
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/files/rename", spaceId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceRenameFileDto.builder()
                        .filePath("/")
                        .existingFilename("hello.txt")
                        .newName("hello.txt")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/files/rename", spaceId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceRenameFileDto.builder()
                        .filePath("/")
                        .existingFilename("hello.txt")
                        .newName("hEllO.txt")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isNoContent());

        // rename hello.txt to hello-world.txt
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/files/rename", spaceId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceRenameFileDto.builder()
                    .filePath("/")
                    .existingFilename("hEllO.txt")
                    .newName("hello-world.txt")
                    .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isNoContent());

        // confirm name change
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)))
                .andExpect(jsonPath("$.documents[?(@.key == 'hello-world.txt')]").exists());

        // rename /docs/names.txt to /docs/hello2.txt (conflict)
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/files/rename", spaceId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceRenameFileDto.builder()
                        .filePath("/docs")
                        .existingFilename("names.txt")
                        .newName("hello2.txt")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isConflict());
    }

    // helper to create spaces with files
    private UUID createSpaceWithFiles(String spaceName) throws Exception {
        // create space
        MvcResult result = mockMvc.perform(post(ENDPOINT_V2 + "/spaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceRequestDto
                        .builder()
                        .name(spaceName)
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", equalTo(spaceName)))
                .andReturn();

        UUID spaceId = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));

        // make folder "docs"
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/folders", spaceId)
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
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload", spaceId.toString())
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
        MockMultipartFile names
                = new MockMultipartFile(
                "file",
                "names.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "names".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=/docs", spaceId.toString())
                .file(file2)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=/docs", spaceId.toString())
                .file(names)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)));  // just 2 things at root - the docs folder and hello.txt

        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/docs", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)))  // just 2 files in the docs folder
                .andExpect(jsonPath("$.documents[?(@.key == 'hello2.txt')]").exists())
                .andExpect(jsonPath("$.documents[?(@.key == 'names.txt')]").exists());

        return spaceId;
    }

    @Transactional
    @Rollback
    @Test
    void testBatchUserAddOps() throws Exception {
        UUID spaceId = createSpaceWithFiles("space-test");
        FileInputStream filestream = new FileInputStream("src/test/resources/dashboard-user-csv/happy-case.csv");
        MockMultipartFile file = new MockMultipartFile("file", "happy-case.csv","text/csv" , filestream);
        mockMvc.perform(multipart(ENDPOINT_V2 +"/spaces/{id}/batchUsers", spaceId.toString())
                .file(file)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // add the happy case list of users over the REST interface
        mockMvc.perform(get(ENDPOINT_V2 +"/spaces/{id}/users/dashboard", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                // 4 people imported
                .andExpect(jsonPath("$.data", hasSize(4)))
                // user given - WRITE and MEMBERSHIP and READ explicitly
                .andExpect(jsonPath("$.data[?(@.email == '1@tron.dev')].privileges[*].type", hasItem("WRITE")))
                .andExpect(jsonPath("$.data[?(@.email == '1@tron.dev')].privileges[*].type", hasItem("MEMBERSHIP")))
                .andExpect(jsonPath("$.data[?(@.email == '1@tron.dev')].privileges[*].type", hasItem("READ")))
                // user given - READ explicitly
                .andExpect(jsonPath("$.data[?(@.email == '2@tron.dev')].privileges[*].type", not(hasItem("WRITE"))))
                .andExpect(jsonPath("$.data[?(@.email == '2@tron.dev')].privileges[*].type", not(hasItem("MEMBERSHIP"))))
                .andExpect(jsonPath("$.data[?(@.email == '2@tron.dev')].privileges[*].type", hasItem("READ")))
                //  user given just WRITE - but gets WRITE and READ
                .andExpect(jsonPath("$.data[?(@.email == '3@test.mil')].privileges[*].type", hasItem("WRITE")))
                .andExpect(jsonPath("$.data[?(@.email == '3@test.mil')].privileges[*].type", not(hasItem("MEMBERSHIP"))))
                .andExpect(jsonPath("$.data[?(@.email == '3@test.mil')].privileges[*].type", hasItem("READ")))
                //  user given just MEMBERSHIP - but gets WRITE and READ too (all three)
                .andExpect(jsonPath("$.data[?(@.email == '4@test.mil')].privileges[*].type", hasItem("WRITE")))
                .andExpect(jsonPath("$.data[?(@.email == '4@test.mil')].privileges[*].type", hasItem("MEMBERSHIP")))
                .andExpect(jsonPath("$.data[?(@.email == '4@test.mil')].privileges[*].type", hasItem("READ")));

        // now lets mimic doing user management from the UI
        // demote 4@test.mil person to just have WRITE (no MEMBERSHIP privilege anymore)
        mockMvc.perform(post(ENDPOINT_V2 +"/spaces/{id}/users", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceDashboardMemberRequestDto.builder()
                        .email("4@test.mil")
                        .privileges(Lists.newArrayList(ExternalDocumentSpacePrivilegeType.WRITE))
                        .build())))
                .andExpect(status().isNoContent());

        // verify
        mockMvc.perform(get(ENDPOINT_V2 +"/spaces/{id}/users/dashboard", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.email == '4@test.mil')].privileges[*].type", hasItem("WRITE")))
                .andExpect(jsonPath("$.data[?(@.email == '4@test.mil')].privileges[*].type", not(hasItem("MEMBERSHIP"))))
                .andExpect(jsonPath("$.data[?(@.email == '4@test.mil')].privileges[*].type", hasItem("READ")));

        // demote 4@test.mil person to just have READ (no MEMBERSHIP or WRITE privilege anymore)
        mockMvc.perform(post(ENDPOINT_V2 +"/spaces/{id}/users", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceDashboardMemberRequestDto.builder()
                        .email("4@test.mil")
                        .privileges(Lists.newArrayList())
                        .build())))
                .andExpect(status().isNoContent());

        // verify
        mockMvc.perform(get(ENDPOINT_V2 +"/spaces/{id}/users/dashboard", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.email == '4@test.mil')].privileges[*].type", not(hasItem("WRITE"))))
                .andExpect(jsonPath("$.data[?(@.email == '4@test.mil')].privileges[*].type", not(hasItem("MEMBERSHIP"))))
                .andExpect(jsonPath("$.data[?(@.email == '4@test.mil')].privileges[*].type", hasItem("READ")));

        // promote 4@test.mil back to ADMIN (should get all three)
        mockMvc.perform(post(ENDPOINT_V2 +"/spaces/{id}/users", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceDashboardMemberRequestDto.builder()
                        .email("4@test.mil")
                        .privileges(Lists.newArrayList(ExternalDocumentSpacePrivilegeType.MEMBERSHIP))
                        .build())))
                .andExpect(status().isNoContent());

        // verify
        mockMvc.perform(get(ENDPOINT_V2 +"/spaces/{id}/users/dashboard", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.email == '4@test.mil')].privileges[*].type", hasItem("WRITE")))
                .andExpect(jsonPath("$.data[?(@.email == '4@test.mil')].privileges[*].type", hasItem("MEMBERSHIP")))
                .andExpect(jsonPath("$.data[?(@.email == '4@test.mil')].privileges[*].type", hasItem("READ")));

        // delete the member from the space
        mockMvc.perform(delete(ENDPOINT_V2 +"/spaces/{id}/users/dashboard", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(Lists.newArrayList("4@test.mil"))))
                .andExpect(status().isNoContent());

        // verify
        mockMvc.perform(get(ENDPOINT_V2 +"/spaces/{id}/users/dashboard", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)));
    }

    @Transactional
    @Rollback
    @Test
    void testWebDavOps() throws Exception {
        UUID spaceId = createSpaceWithFiles("some-space");

        // should have this structure ready for us
        // / <root>
        // |- docs/
        // |  |- hello2.txt
        // |  |- names.txt
        // |- hello.txt

        // do a PROPFIND
        mockMvc.perform(MockMvcRequestBuilders.request("PROPFIND", URI.create(String.format("/v2/document-space-dav/%s/", spaceId)))
                .header("depth", "0")
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isMultiStatus());

        // with a depth of 1
        // do a PROPFIND
        mockMvc.perform(MockMvcRequestBuilders.request("PROPFIND", URI.create(String.format("/v2/document-space-dav/%s/", spaceId)))
                .header("depth", "1")
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isMultiStatus());

        // do the OPTIONS fetch
        mockMvc.perform(options(String.format("/v2/document-space-dav/%s/", spaceId))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(header().exists("allow"));

        // do a MKCOL
        mockMvc.perform(MockMvcRequestBuilders.request("MKCOL", URI.create(String.format("/v2/document-space-dav/%s/test2", spaceId)))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isCreated());

        // do a GET
        mockMvc.perform(get(String.format("/v2/document-space-dav/%s/hello.txt", spaceId))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());
    }

    @Transactional
    @Rollback
    @Test
    void testArchiveRestoreOps() throws Exception {
        UUID spaceId = createSpaceWithFiles("some-space");

        // should have this structure ready for us
        // / <root>
        // |- docs/
        // |  |- hello2.txt
        // |  |- names.txt
        // |- hello.txt

         // Add a file with the same name to two different folders in the same space
         // "Remove" both files so they are archived
         // Restore one of the files from the Archive page -> should leave non-restored one in Archived Items

        MockMultipartFile file
                = new MockMultipartFile(
                "file",
                "hello2.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Hello, World!".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload", spaceId.toString()).file(file)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // archive hello2.txt from docs/
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}/archive", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceArchiveItemsDto.builder()
                        .currentPath("/docs")
                        .itemsToArchive(Lists.newArrayList("hello2.txt"))
                        .build())))
                .andExpect(status().isNoContent());

        // archive hello2.txt from root /
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}/archive", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceArchiveItemsDto.builder()
                        .currentPath("/")
                        .itemsToArchive(Lists.newArrayList("hello2.txt"))
                        .build())))
                .andExpect(status().isNoContent());

        // check both are in Archived Items
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/archived/contents", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)))
                .andExpect(jsonPath("$.documents[0].key", equalTo("hello2.txt")))
                .andExpect(jsonPath("$.documents[1].key", equalTo("hello2.txt")));

        // now restore the one from docs/ originally
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/unarchive", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceUnArchiveItemsDto.builder()
                        .itemsToUnArchive(Lists.newArrayList("/docs/hello2.txt"))
                        .build())))
                .andExpect(status().isNoContent());

        // check one left in archived
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/archived/contents", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(1)))
                .andExpect(jsonPath("$.documents[0].key", equalTo("hello2.txt")))
                .andExpect(jsonPath("$.documents[0].path", equalTo("/")));
    }

    @Transactional
    @Rollback
    @Test
    void testZippingFolder() throws Exception {
        UUID spaceId = createSpaceWithFiles("some-space");

        // should have this structure ready for us
        // / <root>
        // |- docs/
        // |  |- hello2.txt
        // |  |- names.txt
        // |- hello.txt

        // archive hello2.txt from docs/
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}/archive", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceArchiveItemsDto.builder()
                        .currentPath("/docs")
                        .itemsToArchive(Lists.newArrayList("hello2.txt"))
                        .build())))
                .andExpect(status().isNoContent());

        // verify
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/archived/contents", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(1)));

        // make subdir in docs
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/folders", spaceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceCreateFolderDto.builder()
                        .folderName("stuff")
                        .path("/docs")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullPathSpec", equalTo("docs/stuff")));

        // update file to space
        MockMultipartFile file
                = new MockMultipartFile(
                "file",
                "hello.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Hello, World!".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=/docs/stuff", spaceId.toString()).file(file)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // check structure
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[*].key", hasSize(2)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("hello.txt")))
                .andExpect(jsonPath("$.documents[*].key", hasItem("docs")));

        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/docs", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[*].key", hasSize(2)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("names.txt")))
                .andExpect(jsonPath("$.documents[*].key", hasItem("stuff")));

        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/docs/stuff", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[*].key", hasSize(1)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("hello.txt")));

        // now zip/download the docs folder
        // should have structure like this in the zip file
        // |- docs/
        // |  |- stuff/
        // |  |  |- hello.txt
        // |  |- hello2.txt
        String tmpdir = Files.createTempDir().getAbsolutePath();
        File zipFile = new File(tmpdir + File.separator + "files.zip");
        FileOutputStream fos = new FileOutputStream(zipFile);
        documentSpaceService.downloadAndWriteCompressedFiles(spaceId, "/", Set.of("docs"), fos, admin.getEmail());
        fos.close();

        ZipFile zf = new ZipFile(zipFile);
        Enumeration<? extends ZipEntry> entries = zf.entries();
        List<String> contents = new ArrayList<>();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            contents.add(entry.getName());
        }
        zf.close();
        FileUtils.deleteDirectory(new File(tmpdir));
        assertFalse(contents.contains("hello.txt"));
        assertFalse(contents.contains("docs/hello2.txt"));
        assertTrue(contents.contains("docs/names.txt"));
        assertTrue(contents.contains("docs/stuff/hello.txt"));
    }

    @Transactional
    @Rollback
    @Test
    void testFileStatCommand() throws Exception {
        UUID spaceId = createSpaceWithFiles("my-space");

        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{space}/stat", spaceId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpacePathItemsDto.builder()
                    .currentPath("/")
                    .items(Lists.newArrayList())
                    .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

       // GO path
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{space}/stat", spaceId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpacePathItemsDto.builder()
                        .currentPath("/")
                        .items(Lists.newArrayList("hello.txt", "newFile.txt"))
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Transactional
    @Rollback
    @Test
    void testUploadFileToPathThatDoesNotExist() throws Exception {

        // test that we can create paths (folders of which) on the fly that don't exist
        UUID spaceId = createSpaceWithFiles("space1");

        // sent up the file
        MockMultipartFile file
                = new MockMultipartFile(
                "file",
                "hello-world.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Hello, World!".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=/some/deep/path/within/the/space", spaceId.toString()).file(file)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // confirm the operation
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/some/deep/path/within/the/space", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(1)))
                .andExpect(jsonPath("$.documents[0].key", equalTo("hello-world.txt")));

        // upload another file to that same location
        MockMultipartFile file2
                = new MockMultipartFile(
                "file",
                "hello-world2.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Hello, World2!".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=/some/deep/path/within/the/space", spaceId.toString()).file(file2)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // confirm the operation
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/some/deep/path/within/the/space", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("hello-world2.txt")));

        // upload to a level just before the last location
        MockMultipartFile file3
                = new MockMultipartFile(
                "file",
                "hello-world3.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Hello, World3!".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=/some/deep/path/within/the", spaceId.toString()).file(file3)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // confirm the operation
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/some/deep/path/within/the", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("hello-world3.txt")));

        // do same thing but put trailing slash on the path to be created
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=/some/deep/path/within/the/newspace/", spaceId.toString()).file(file3)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // confirm the operation
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/some/deep/path/within/the/newspace", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(1)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("hello-world3.txt")));

        // confirm the MAX_DEPTH restriction is enforced
        MockMultipartFile file4
                = new MockMultipartFile(
                "file",
                "hello-world4.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Hello, World4!".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=/some/deep/path/within/the/space/that/is/very/very/long/indeed/and/should/fail/the/depth/test/that/is/in/place", spaceId.toString()).file(file4)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isBadRequest());

        // confirm that we validate new folder names
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=/some/deep/path/invalid.folder.name.with.dots/the/space", spaceId.toString()).file(file4)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isBadRequest());
    }

    @Transactional
    @Rollback
    @Test
    void testMobileSpaceOps() throws Exception {

        // test we can get list of spaces that user can access along with their privs to it
        //  and the default space (if its set)

        UUID space1 = createSpaceWithFiles("space1");
        UUID space2 = createSpaceWithFiles("space2");
        UUID space3 = createSpaceWithFiles("space3");

        // test that a DASHBOARD_ADMIN can see all spaces and is listed as ADMIN in all of them
        mockMvc.perform(get("/v2/document-space-mobile/spaces")
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spaces[0].privilege", equalTo("ADMIN")))
                .andExpect(jsonPath("$.spaces[1].privilege", equalTo("ADMIN")))
                .andExpect(jsonPath("$.spaces[2].privilege", equalTo("ADMIN")));

        // create a new user that can only access spaces - 1 and 2
        DashboardUser someUser = DashboardUser.builder()
                .email("dude@test.gov")
                .privileges(Set.of(
                        privRepo.findByName("DOCUMENT_SPACE_USER").orElseThrow(() -> new RecordNotFoundException("No Document Space User Priv")),
                        privRepo.findByName("DASHBOARD_USER").orElseThrow(() -> new RecordNotFoundException("No DASH_BOARD USER"))
                ))
                .build();

        someUser = dashRepo.save(someUser);
        documentSpaceService.addDashboardUserToDocumentSpace(space1, DocumentSpaceDashboardMemberRequestDto.builder()
                .email("dude@test.gov")
                .privileges(Lists.newArrayList())
                .build());
        documentSpaceService.addDashboardUserToDocumentSpace(space2, DocumentSpaceDashboardMemberRequestDto.builder()
                .email("dude@test.gov")
                .privileges(Lists.newArrayList(ExternalDocumentSpacePrivilegeType.WRITE))
                .build());

        // test that a new user can see spaces 1 and 2 and is just a viewer to space 1 and editor to space 2
        mockMvc.perform(get("/v2/document-space-mobile/spaces")
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(someUser.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spaces", hasSize(2)))
                .andExpect(jsonPath("$.defaultSpace", equalTo(null)))
                .andExpect(jsonPath("$.spaces[0].privilege", equalTo("VIEWER")))
                .andExpect(jsonPath("$.spaces[1].privilege", equalTo("EDITOR")));

        someUser.setDefaultDocumentSpaceId(space2);
        dashRepo.save(someUser);

        // check default space saved
        mockMvc.perform(get("/v2/document-space-mobile/spaces")
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(someUser.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spaces", hasSize(2)))
                .andExpect(jsonPath("$.defaultSpace.id", equalTo(space2.toString())))
                .andExpect(jsonPath("$.defaultSpace.privilege", equalTo("EDITOR")))
                .andExpect(jsonPath("$.spaces[0].privilege", equalTo("VIEWER")))
                .andExpect(jsonPath("$.spaces[1].privilege", equalTo("EDITOR")));

        //
        // test we can get list of a space's directory and see what of it we have as a favorite(s)

        // look in the /docs folder, should not be any favorites
        mockMvc.perform(get("/v2/document-space-mobile/spaces/{id}/contents?path=/docs", space2.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(someUser.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)))
                .andExpect(jsonPath("$.documents[?(@.favorite == true)]", hasSize(0)));

        // make some names.txt a favorite
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/collection/favorite/", space2.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(someUser.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpacePathItemsDto.builder()
                        .currentPath("/docs")
                        .items(Lists.newArrayList("names.txt"))
                        .build())))
                .andExpect(status().isCreated());

        // verify its reflected as such in the api response for that directory for that user
        mockMvc.perform(get("/v2/document-space-mobile/spaces/{id}/contents?path=/docs", space2.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(someUser.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)))
                .andExpect(jsonPath("$.documents[?(@.favorite == true)]", hasSize(1)));

        // ask same endpoint but as ADMIN - item should not be a favorite
        mockMvc.perform(get("/v2/document-space-mobile/spaces/{id}/contents?path=/docs", space2.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)))
                .andExpect(jsonPath("$.documents[?(@.favorite == true)]", hasSize(0)));

    }

    @Transactional
    @Rollback
    @Test
    void testGetFolderSize() throws Exception {
        UUID spaceId = UUID.randomUUID();

        // create an empty space
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces").contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceRequestDto.builder().id(spaceId).name("test1").build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name", equalTo("test1")));

        // create a new folder named "docs"
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/folders", spaceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceCreateFolderDto.builder()
                        .folderName("docs")
                        .path("/")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullPathSpec", equalTo("docs")));

        // add a file to "docs" named "hello2.txt" - note its size - should be 20bytes
        MockMultipartFile file
                = new MockMultipartFile(
                "file",
                "hello2.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Hello, World!!!!!!!!".getBytes()
        );
        // upload it
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=/docs", spaceId.toString()).file(file)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // verify its size - 20 bytes
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/folder-size?path=/docs", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size", equalTo(20)));

        // now get a root listing and verify the "docs" folder is 20 bytes contained therein
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(1)))
                .andExpect(jsonPath("$.documents[0].key", equalTo("docs")))
                .andExpect(jsonPath("$.documents[0].folder", equalTo(true)))
                .andExpect(jsonPath("$.documents[0].size", equalTo(20)));

        // create a new folder named "stuff" within "docs"
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/folders", spaceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceCreateFolderDto.builder()
                        .folderName("stuff")
                        .path("/docs")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullPathSpec", equalTo("docs/stuff")));

        // add a file to "/docs/stuff" named "hello3.txt" - note its size - should be 30bytes
        MockMultipartFile file2
                = new MockMultipartFile(
                "file",
                "hello3.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Hello, World!!!!!!!!!!!!!!!!!!".getBytes()
        );
        // upload it
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=/docs/stuff", spaceId.toString()).file(file2)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // now get a listing of "/docs" and verify the "stuff" folder is 30 bytes contained therein
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/folder-size?path=/docs/stuff", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size", equalTo(30)));

        // now get a listing of "/" and verify the "docs" folder is 50 bytes contained therein
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/folder-size?path=/", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size", equalTo(50)));

        // now add a file at the root level
        MockMultipartFile file3
                = new MockMultipartFile(
                "file",
                "hello_root.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Hello".getBytes()
        );
        // upload it
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload", spaceId.toString()).file(file3)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // root listing now 55 bytes
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/folder-size?path=/", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size", equalTo(55)));

        // so now lets archive the file that's inside /docs/stuff (hello3.txt)
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}/archive", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceArchiveItemsDto.builder()
                        .currentPath("/docs/stuff")
                        .itemsToArchive(Lists.newArrayList("hello3.txt"))
                        .build())))
                .andExpect(status().isNoContent());

        // verify the /docs/stuff folder is 0 bytes
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/folder-size?path=/docs/stuff", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size", equalTo(0)));

        // verify the root listing reports /docs as just 20 bytes now
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/folder-size?path=/docs", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size", equalTo(20)));

        // now unarchive that file and make sure it goes back up
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/unarchive", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceUnArchiveItemsDto.builder()
                        .itemsToUnArchive(Lists.newArrayList("/docs/stuff/hello3.txt"))
                        .build())))
                .andExpect(status().isNoContent());

        // verify the /docs back to 50 bytes now
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/folder-size?path=/docs", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size", equalTo(50)));

    }

    @Test
    @Rollback
    @Transactional
    void testMoveFile() throws Exception {

        // test we can move files within the space
        UUID space1 = createSpaceWithFiles("space1");

        // moving files should create directories on-the-fly within the space if they don't exist
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/move", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"docs/names.txt\" : \"new-docs/new-names.txt\" }"))
                .andExpect(status().isNoContent());

        // verify old file is "gone"
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/docs", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(1)));

        // moved to new location and name
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/new-docs", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(1)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("new-names.txt")));

        // should be able to move to a new file within the same folder location (essentially a fancy re-name)
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/move", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"new-docs/new-names.txt\" : \"new-docs/new-names2.txt\" }"))
                .andExpect(status().isNoContent());

        // verify
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/new-docs", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(1)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("new-names2.txt")));

        // get file size of original file before we copy over it
        S3PaginationDto dto = documentSpaceService.listFiles(space1, null, Integer.MAX_VALUE);
        int count0 = dto.getDocuments().size();
        long size0 = dto.getDocuments().stream().filter(item -> item.getKey().contains("hello2.txt")).findFirst().get().getSize();

        // should be able to move an item on top (over) an existing item
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/move", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"new-docs/new-names2.txt\" : \"docs/hello2.txt\" }"))
                .andExpect(status().isNoContent());

        // verify gone from old location
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/new-docs", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(0)));
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/docs", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(1)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("hello2.txt")));

        dto = documentSpaceService.listFiles(space1, null, Integer.MAX_VALUE);
        int count1 = dto.getDocuments().size();
        long size1 = dto.getDocuments().stream().filter(item -> item.getKey().contains("hello2.txt")).findFirst().get().getSize();

        // make sure we copied over the old file (ensure that we really did replace the file with different contents)
        // and that there is one fewer file in our space now since this was a move operation
        assertTrue(size0 != size1);
        assertEquals(count0 - 1, count1);
    }

    @Test
    @Transactional
    @Rollback
    void testMovingFilesAroundAtRootLevelOfSpace() throws Exception {
        // test we can move files within the space, at the root level, where some weirdness can happen since the "root"
        // level is a special type of "folder"

        UUID space1 = createSpaceWithFiles("space1");

        // upload another file to the root level named data.txt
        MockMultipartFile data
                = new MockMultipartFile(
                "file",
                "data.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "I am Data, Destroyer of Worlds".getBytes()
        );
        // upload it
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload", space1.toString()).file(data)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // move "hello.txt" to "hello-new.txt"
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/move", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"hello.txt\" : \"hello-new.txt\" }"))
                .andExpect(status().isNoContent());

        // verify
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(3)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("hello-new.txt")))
                .andExpect(jsonPath("$.documents[*].key", not(hasItem("hello.txt"))));

        // get file size of original file before we copy over it
        S3PaginationDto dto = documentSpaceService.listFiles(space1, null, Integer.MAX_VALUE);
        int count0 = dto.getDocuments().size();
        long size0 = dto.getDocuments().stream().filter(item -> item.getKey().contains("data.txt")).findFirst().get().getSize();

        // move "hello-new.txt" to "data.txt" (an existent file)
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/move", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"hello-new.txt\" : \"data.txt\" }"))
                .andExpect(status().isNoContent());

        // verify
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)))
                .andExpect(jsonPath("$.documents[*].key", not(hasItem("hello-new.txt"))))
                .andExpect(jsonPath("$.documents[*].key", not(hasItem("hello.txt"))))
                .andExpect(jsonPath("$.documents[*].key", hasItem("data.txt")));

        dto = documentSpaceService.listFiles(space1, null, Integer.MAX_VALUE);
        int count1 = dto.getDocuments().size();
        long size1 = dto.getDocuments().stream().filter(item -> item.getKey().contains("data.txt")).findFirst().get().getSize();

        // make sure we copied over the old file (ensure that we really did replace the file with different contents)
        // and that there is one fewer file in our space now since this was a move operation
        assertTrue(size0 != size1);
        assertEquals(count0 - 1, count1);
    }

    @Test
    @Transactional
    @Rollback
    void testMoveFileWithInvalidNamesFails() throws Exception {
        UUID space1 = createSpaceWithFiles("space1");

        // should fail to move to invalid new folder names
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/move", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"docs/names.txt\" : \"new...docs/new-names.txt\" }"))
                .andExpect(status().isBadRequest());

        // should fail to move to invalid filename
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/move", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"docs/names.txt\" : \"new-docs/new..%names.txt\" }"))
                .andExpect(status().isBadRequest());

        // should fail to move onto itself (otherwise you end up with nothing stored in S3)
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/move", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"docs/names.txt\" : \"docs/names.txt\" }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    @Rollback
    void testMultiMoveFilesAreTransactional() throws Exception {

        // test moving multiple files with a failure in the middle of process doesn't
        // void the ones that successfully transferred before the exception
        //
        // this test covers copying files too since those all go through the same code path...

        UUID space1 = createSpaceWithFiles("space1");

        // should have this structure ready for us
        // / <root>
        // |- docs/
        // |  |- hello2.txt
        // |  |- names.txt
        // |- hello.txt

        // move hello.txt to the docs/ folder so we have 3 files in there
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/move", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"hello.txt\" : \"docs/hello.txt\" }"))
                .andExpect(status().isNoContent());

        // verify
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/docs", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(3)));

        // now move the 3 files inside docs/ to a folder named new-folder/ but make the moving
        // of names.txt fails
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/move", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"/docs/hello2.txt\" : \"new-folder/hello2.txt\", \"/docs/names.txt\" : \"/new-folder/nam$%es....txt\", \"/docs/hello.txt\" : \"/new-folder/hello.txt\" }"))
                .andExpect(status().isBadRequest());

        // but make sure we copied over hello2 txt to new-folder (before we had the next file fail)
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/docs", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)));
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/new-folder", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(1)));
    }

    @Test
    @Transactional
    @Rollback
    void testMovingWholeFoldersWorks() throws Exception {
        // test moving entire folders along with discrete files works to another folder

        UUID space1 = createSpaceWithFiles("space1");

        // should have this structure ready for us
        // / <root>
        // |- docs/
        // |  |- hello2.txt
        // |  |- names.txt
        // |- hello.txt

        // make a new folder called - "old" - and move docs/ and the hello.txt file to it
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/folders", space1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceCreateFolderDto.builder()
                        .folderName("old")
                        .path("/")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isCreated());

        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/move", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"docs\" : \"old/docs\", \"hello.txt\" : \"old/hello.txt\" }"))
                .andExpect(status().isNoContent());

        // verify
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(1)))
                .andExpect(jsonPath("$.documents[0].key", equalTo("old")));
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/old", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("docs")))
                .andExpect(jsonPath("$.documents[*].key", hasItem("hello.txt")));
    }

    @Test
    @Rollback
    @Transactional
    void testMoveWholeFolderOverExistingFolder() throws Exception {
        // test we can move a whole folder over and replace an existing folder with its own contents
        //  that will then get erased

        UUID space1 = createSpaceWithFiles("space1");

        // create a new folder named "other"
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/folders", space1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceCreateFolderDto.builder()
                        .folderName("other")
                        .path("/")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isCreated());

        // put file into other/
        MockMultipartFile file
                = new MockMultipartFile(
                "file",
                "other-file.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Other data file!".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=/other", space1.toString()).file(file)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // create a new folder inside other/ named "other-sub-folder/"
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/folders", space1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceCreateFolderDto.builder()
                        .folderName("other-sub-folder")
                        .path("/other")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isCreated());

        // put file into other/other-sub-folder
        MockMultipartFile file2
                = new MockMultipartFile(
                "file",
                "other-file2.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Other data file2!".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=/other/other-sub-folder", space1.toString()).file(file2)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // should have this structure ready for us
        // / <root>
        // |- other/
        // |  |- other-file.txt
        // |  |- other-sub-folder/
        // |  |   |- other-file2.txt
        // |- docs/
        // |  |- hello2.txt
        // |  |- names.txt
        // |- hello.txt

        // now move docs/ over top of other/  -- all of other original contents should go away
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/move", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"docs\" : \"other\" }"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("hello.txt")))
                .andExpect(jsonPath("$.documents[*].key", hasItem("other")))
                .andExpect(jsonPath("$.documents[*].key", not(hasItem("hello"))));

        // now make sure there's no trace of what was in other/ folder before in S3
        S3PaginationDto dto = documentSpaceService.listFiles(space1, null, Integer.MAX_VALUE);
        assertFalse(dto.getDocuments().stream().anyMatch(item -> item.getKey().contains("other-file.txt")));
        assertFalse(dto.getDocuments().stream().anyMatch(item -> item.getKey().contains("other-file2.txt")));

    }

    @Test
    @Rollback
    @Transactional
    void testCopyFile() throws Exception {

        // test we can copy files within the space..

        UUID space1 = createSpaceWithFiles("space1");

        // grant go@army.mil to the destination space as an editor
        DashboardUser someUser = DashboardUser.builder()
                .email("go@army.mil")
                .privileges(Set.of(
                        privRepo.findByName("DOCUMENT_SPACE_USER").orElseThrow(() -> new RecordNotFoundException("No Document Space User Priv")),
                        privRepo.findByName("DASHBOARD_USER").orElseThrow(() -> new RecordNotFoundException("No DASH_BOARD USER"))
                ))
                .build();
        dashRepo.save(someUser);

        documentSpaceService.addDashboardUserToDocumentSpace(space1, DocumentSpaceDashboardMemberRequestDto.builder()
                .email("go@army.mil")
                .privileges(Lists.newArrayList(ExternalDocumentSpacePrivilegeType.WRITE))
                .build());

        // copying files should create directories on-the-fly within the space if they don't exist
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/copy", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(someUser.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"docs/names.txt\" : \"new-docs/new-names.txt\" }"))
                .andExpect(status().isNoContent());

        // verify old file is still there
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/docs", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(someUser.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)));

        // copied to new location and name
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/new-docs", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(someUser.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(1)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("new-names.txt")));

        // should be able to copy to a new file within the same folder location
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/copy", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(someUser.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"new-docs/new-names.txt\" : \"new-docs/new-names2.txt\" }"))
                .andExpect(status().isNoContent());

        // verify
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/new-docs", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(someUser.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("new-names2.txt")));

        // test copy file to another folder but already like-named file there
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/copy", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(someUser.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"docs/names.txt\" : \"new-docs/names.txt\" }"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/copy", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(someUser.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"docs/names.txt\" : \"new-docs/names.txt\" }"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/new-docs", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(someUser.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[*].key", hasItem("names.txt")))
                .andExpect(jsonPath("$.documents[*].key", hasItem("names - (Copy 1).txt")));
    }

    @Test
    @Transactional
    @Rollback
    void testCopyingToSameLocationIsOk() throws Exception {

        // test copying to same path and with same proposed name is OK - just we have to append a copy # to it
        UUID space1 = createSpaceWithFiles("space1");
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/copy", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"docs/names.txt\" : \"docs/names.txt\" }"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/docs", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[*].key", hasItem("names.txt")))
                .andExpect(jsonPath("$.documents[*].key", hasItem("names - (Copy 1).txt")));

        // do it again
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/copy", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"docs/names.txt\" : \"docs/names.txt\" }"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/docs", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[*].key", hasItem("names.txt")))
                .andExpect(jsonPath("$.documents[*].key", hasItem("names - (Copy 1).txt")))
                .andExpect(jsonPath("$.documents[*].key", hasItem("names - (Copy 2).txt")));

        // but this should work (Copying to a whole new name)
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/copy", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"docs/names.txt\" : \"docs/new-names.txt\" }"))
                .andExpect(status().isNoContent());

        // get file size of original file before we copy over it
        S3PaginationDto dto = documentSpaceService.listFiles(space1, null, Integer.MAX_VALUE);
        int count0 = dto.getDocuments().size();
        long size0 = dto.getDocuments().stream().filter(item -> item.getKey().contains("hello2.txt")).findFirst().get().getSize();

        // as should this too (copying over some OTHER file)
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/copy", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"docs/names.txt\" : \"docs/hello2.txt\" }"))
                .andExpect(status().isNoContent());

        dto = documentSpaceService.listFiles(space1, null, Integer.MAX_VALUE);
        int count1 = dto.getDocuments().size();
        long size1 = dto.getDocuments().stream().filter(item -> item.getKey().contains("hello2.txt")).findFirst().get().getSize();

        // make sure we didn't copy over the old file
        // and that the number in the S3 bin is +1
        assertTrue(size0 == size1);
        assertEquals(count0 + 1, count1);
    }

    @Test
    @Transactional
    @Rollback
    void testCopyingWholeFoldersWorks() throws Exception {
        // test copying entire folders along with discrete files works to another folder

        UUID space1 = createSpaceWithFiles("space1");

        // should have this structure ready for us
        // / <root>
        // |- docs/
        // |  |- hello2.txt
        // |  |- names.txt
        // |- hello.txt

        // make a new folder called - "old" - and COPY docs/ and the hello.txt file to it
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/folders", space1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceCreateFolderDto.builder()
                        .folderName("old")
                        .path("/")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isCreated());

        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/copy", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"docs\" : \"old/docs\", \"hello.txt\" : \"old/hello.txt\" }"))
                .andExpect(status().isNoContent());

        // verify
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(3)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("old")))
                .andExpect(jsonPath("$.documents[*].key", hasItem("docs")))
                .andExpect(jsonPath("$.documents[*].key", hasItem("hello.txt")));

        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/old", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("docs")))
                .andExpect(jsonPath("$.documents[*].key", hasItem("hello.txt")));

        // dig in and really make sure our copies happened correctly
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/old/docs", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("hello2.txt")))
                .andExpect(jsonPath("$.documents[?(@.key == 'hello2.txt')].folder", hasItem(false)))
                .andExpect(jsonPath("$.documents[?(@.key == 'names.txt')].folder", hasItem(false)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("names.txt")));

        // should have two of these file names present
        S3PaginationDto dto = documentSpaceService.listFiles(space1, null, Integer.MAX_VALUE);
        assertEquals(2, (int) dto.getDocuments().stream().filter(item -> item.getKey().contains("hello2.txt")).count());
        assertEquals(2, dto.getDocuments().stream().filter(item -> item.getKey().contains("names.txt")).count());
    }

    @Test
    @Rollback
    @Transactional
    void testCopyWholeFolderOverExistingFolder() throws Exception {
        // test we can COPY a whole folder over and replace an existing folder with its own contents
        //  that will then get erased by virtue of being copied over

        UUID space1 = createSpaceWithFiles("space1");

        // create a new folder named "other"
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/folders", space1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceCreateFolderDto.builder()
                        .folderName("other")
                        .path("/")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isCreated());

        // put file into other/
        MockMultipartFile file
                = new MockMultipartFile(
                "file",
                "other-file.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Other data file!".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=/other", space1.toString()).file(file)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // create a new folder inside other/ named "other-sub-folder/"
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/folders", space1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceCreateFolderDto.builder()
                        .folderName("other-sub-folder")
                        .path("/other")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isCreated());

        // put file into other/other-sub-folder
        MockMultipartFile file2
                = new MockMultipartFile(
                "file",
                "other-file2.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Other data file2!".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=/other/other-sub-folder", space1.toString()).file(file2)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // should have this structure ready for us
        // / <root>
        // |- other/
        // |  |- other-file.txt
        // |  |- other-sub-folder/
        // |  |   |- other-file2.txt
        // |- docs/
        // |  |- hello2.txt
        // |  |- names.txt
        // |- hello.txt

        // now COPY docs/ over top of other/  -- but really a "docs - (Copy 1)/" is made
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/copy", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"docs\" : \"other\" }"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(4)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("hello.txt")))
                .andExpect(jsonPath("$.documents[*].key", hasItem("docs")))
                .andExpect(jsonPath("$.documents[*].key", hasItem("other - (Copy 1)")))
                .andExpect(jsonPath("$.documents[?(@.key == 'other - (Copy 1)')].folder", hasItem(true)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("other")));

        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/docs", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("hello2.txt")))
                .andExpect(jsonPath("$.documents[*].key", hasItem("names.txt")));

        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/other", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("other-file.txt")))
                .andExpect(jsonPath("$.documents[*].key", hasItem("other-sub-folder")));

        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/other - (Copy 1)", space1.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("hello2.txt")))
                .andExpect(jsonPath("$.documents[*].key", hasItem("names.txt")));
    }

    @Test
    @Transactional
    @Rollback
    void testCrossSpaceMoving() throws Exception {

        // test that we can move folders and files across space so long as permissions allow it

        UUID sourceSpace = createSpaceWithFiles("space1");

        // create new empty space
        MvcResult result = mockMvc.perform(post(ENDPOINT_V2 + "/spaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceRequestDto
                        .builder()
                        .name("space2")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", equalTo("space2")))
                .andReturn();

        UUID destinationSpace = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));

        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/move?sourceSpaceId={source}", destinationSpace, sourceSpace)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"docs\" : \"new-docs\" }"))
                .andExpect(status().isNoContent());

        // verify old file is "gone" in source
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/docs", sourceSpace)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isNotFound());

        // verify file was NOT moved within the source space
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/new-docs", sourceSpace)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isNotFound());

        // moved to new location in destination space
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/new-docs", destinationSpace)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("names.txt")))
                .andExpect(jsonPath("$.documents[*].key", hasItem("hello2.txt")));
    }

    @Test
    @Transactional
    @Rollback
    void testCrossSpaceMovingBadActorUser() throws Exception {

        // test that we cannot move folders and files across space if we only have privs to the destination space
        //  tests that we can be sneaky and use an API call to an arbitrary doc space and copy files from it

        UUID sourceSpace = createSpaceWithFiles("space1");

        // create new empty space
        MvcResult result = mockMvc.perform(post(ENDPOINT_V2 + "/spaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceRequestDto
                        .builder()
                        .name("space2")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", equalTo("space2")))
                .andReturn();

        UUID destinationSpace = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));

        // grant go_boilers@purdue.edu to the destination space as an editor
        DashboardUser someUser = DashboardUser.builder()
                .email("go_boilers@purdue.edu")
                .privileges(Set.of(
                        privRepo.findByName("DOCUMENT_SPACE_USER").orElseThrow(() -> new RecordNotFoundException("No Document Space User Priv")),
                        privRepo.findByName("DASHBOARD_USER").orElseThrow(() -> new RecordNotFoundException("No DASH_BOARD USER"))
                ))
                .build();
        dashRepo.save(someUser);

        documentSpaceService.addDashboardUserToDocumentSpace(destinationSpace, DocumentSpaceDashboardMemberRequestDto.builder()
                .email("go_boilers@purdue.edu")
                .privileges(Lists.newArrayList(ExternalDocumentSpacePrivilegeType.WRITE))
                .build());

        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/move?sourceSpaceId={source}", destinationSpace, sourceSpace)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(someUser.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"docs\" : \"new-docs\" }"))
                .andExpect(status().isForbidden());

        // now bless with viewer on the source space and it should work
        documentSpaceService.addDashboardUserToDocumentSpace(sourceSpace, DocumentSpaceDashboardMemberRequestDto.builder()
                .email("go_boilers@purdue.edu")
                .privileges(Lists.newArrayList())
                .build());

        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/move?sourceSpaceId={source}", destinationSpace, sourceSpace)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(someUser.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"docs\" : \"new-docs\" }"))
                .andExpect(status().isNoContent());

        // verify old file is "gone" in source
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/docs", sourceSpace)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(someUser.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isNotFound());

        // verify file was NOT moved within the source space
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/new-docs", sourceSpace)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(someUser.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isNotFound());

        // moved to new location in destination space
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/new-docs", destinationSpace)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(someUser.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("names.txt")))
                .andExpect(jsonPath("$.documents[*].key", hasItem("hello2.txt")));
    }

    @Test
    @Transactional
    @Rollback
    void testCrossSpaceCopying() throws Exception {

        // test that we can copy folders and files across space so long as permissions allow it

        UUID sourceSpace = createSpaceWithFiles("space1");

        // create new empty space
        MvcResult result = mockMvc.perform(post(ENDPOINT_V2 + "/spaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceRequestDto
                        .builder()
                        .name("space2")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", equalTo("space2")))
                .andReturn();

        UUID destinationSpace = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));

        // moving files should create directories on-the-fly within the space if they don't exist
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/copy?sourceSpaceId={source}", destinationSpace, sourceSpace)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"docs/names.txt\" : \"new-docs/new-names.txt\" }"))
                .andExpect(status().isNoContent());

        // verify folder was NOT placed within the source space
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/new-docs", sourceSpace)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isNotFound());

        // moved to new location in destination space
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/new-docs", destinationSpace)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(1)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("new-names.txt")));
    }

    @Test
    @Transactional
    @Rollback
    void testLastModifiedDateWorksOnUploadsAndDownloads() throws Exception {

        // probably one of the most important tests - test that we can preserve a file's date modified
        //  on uploads and downloads

        UUID test1Id = createSpaceWithFiles("space1");

        // update file to space
        MockMultipartFile file
                = new MockMultipartFile(
                "file",
                "coolfile.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Cool, World!".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=/new-docs", test1Id.toString()).file(file)
                .header("Last-Modified", String.valueOf(Instant.from(OffsetDateTime.of(LocalDateTime.of(2014 , 2 , 11, 4, 44, 44), ZoneOffset.UTC)).toEpochMilli()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // check file in space test1
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/new-docs", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[*].key", hasItem("coolfile.txt")))
                .andExpect(jsonPath("$.documents[?(@.key == 'coolfile.txt')].lastModifiedDate", hasItem("2014-02-11T04:44:44.000Z")));

        // dont check the date modified on a single file download right now, since there seems to be no easy way to do
        //  this unless the browser respects the Last-Modified date

        // but we can control the entries in a zip file and the OS that unzips it will respect the dates on those entries on the extraction
        // so, go ahead and download it as a zip file, make sure its date is still the same
        String tmpdir = Files.createTempDir().getAbsolutePath();
        File zipFile = new File(tmpdir + File.separator + "file.zip");
        FileOutputStream fos = new FileOutputStream(zipFile);
        documentSpaceService.downloadAndWriteCompressedFiles(test1Id, "/new-docs", Set.of("coolfile.txt"), fos, admin.getEmail());
        fos.close();

        ZipFile zf = new ZipFile(zipFile);
        Enumeration<? extends ZipEntry> entries = zf.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            assertEquals(Instant.from(OffsetDateTime.of(LocalDateTime.of(2014 , 2 , 11, 4, 44, 44), ZoneOffset.UTC)).toEpochMilli(),
                    Instant.from(OffsetDateTime.of(entry.getTimeLocal(), ZoneOffset.UTC)).toEpochMilli());
        }
        zf.close();
        FileUtils.deleteDirectory(new File(tmpdir));

        // make sure parent folder (in this case the root folder) reports new-docs folder last mod date as the newest date
        //  contained therein (in this case the old 2014 date)
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/", test1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[*].key", hasItem("new-docs")))
                .andExpect(jsonPath("$.documents[?(@.key == 'new-docs')].lastModifiedDate", hasItem("2014-02-11T04:44:44.000Z")));
    }

    @Test
    @Transactional
    @Rollback
    void testDashboardAdminDoesNotGet403ForSpace() throws Exception {

        // tests that DASHBOARD_ADMIN (who can do anything in the doc spaces)
        //  can do a GET to the /api/v2/document-space/spaces/{id}/users/dashboard/privileges/self
        //  and not get a 403 because they're not explicitly assigned to the space... should be a 200

        UUID space1Id = createSpaceWithFiles("space1");
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/users/dashboard/privileges/self", space1Id)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].type", hasItem("MEMBERSHIP")))
                .andExpect(jsonPath("$.data[*].type", hasItem("WRITE")))
                .andExpect(jsonPath("$.data[*].type", hasItem("READ")));

        // now explicitly an entity to the space - and we'll honor that (even tho admins can always do anything)
        //  otherwise could be confusing on the UI
        documentSpaceService.addDashboardUserToDocumentSpace(space1Id, DocumentSpaceDashboardMemberRequestDto.builder()
                .email(admin.getEmail())
                .privileges(Lists.newArrayList(ExternalDocumentSpacePrivilegeType.WRITE))
                .build());

        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/users/dashboard/privileges/self", space1Id)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].type", not(hasItem("MEMBERSHIP"))))
                .andExpect(jsonPath("$.data[*].type", hasItem("WRITE")))
                .andExpect(jsonPath("$.data[*].type", hasItem("READ")));
    }

    @Test
    @Transactional
    @Rollback
    void testSpacesAreTrimmedFromFileAndFolderNames() throws Exception {

        // if I create a folder or I renamed a file/folder and have whitespace on it, the API
        // should trim it before persisting it, because we could end up with files/folder we can't get rid of

        UUID space1Id = createSpaceWithFiles("space1");

        // make folder with space on then end, should get trimmed off
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/folders", space1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceCreateFolderDto.builder()
                        .folderName("dir-with-space ")
                        .path("/")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullPathSpec", equalTo("dir-with-space")));

        // list it - should appear having been persisted with no spaces at the end
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents", space1Id)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[*].key", hasItem("dir-with-space")));

        // now archive it
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}/archive", space1Id)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceArchiveItemsDto.builder()
                        .currentPath("")
                        .itemsToArchive(Lists.newArrayList("dir-with-space"))
                        .build())))
                .andExpect(status().isNoContent());

        // purge it
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}/delete", space1Id)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpacePathItemsDto.builder()
                        .currentPath("")
                        .items(Lists.newArrayList("dir-with-space"))
                        .build())))
                .andExpect(status().isNoContent());
    }

    @Test
    @Transactional
    @Rollback
    void testLastActivityIndication() throws Exception {

        // newly uploaded/updated files should have their last activity timestamp updated
        //  this is different than the last modified timestamp

        UUID space1Id = createSpaceWithFiles("space1");

        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/folders", space1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceCreateFolderDto.builder()
                        .folderName("some-directory")
                        .path("/")
                        .build()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isCreated());

        OffsetDateTime now = OffsetDateTime.now();

        // update file to space
        MockMultipartFile file
                = new MockMultipartFile(
                "file",
                "valentines.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Happy Valentines Day!".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=some-directory", space1Id.toString()).file(file)
                .header("Last-Modified", String.valueOf(Instant.from(OffsetDateTime.of(LocalDateTime.of(2014 , 2 , 14, 4, 44, 44), ZoneOffset.UTC)).toEpochMilli()))
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=some-directory", space1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(1)))
                .andExpect(jsonPath("$.documents[0].key", equalTo("valentines.txt")))
                .andExpect(jsonPath("$.documents[0].lastModifiedDate", equalTo("2014-02-14T04:44:44.000Z")))
                .andReturn();

        Date lastActivity = new ObjectMapper().readValue(result.getResponse().getContentAsString(), S3PaginationDto.class).getDocuments().get(0).getLastActivity();

        // check reported last activity is within 1 min (giving some leeway here for JUnit test)
        assertTrue(OffsetDateTime.ofInstant(lastActivity.toInstant(), ZoneOffset.UTC).isAfter(now.minusMinutes(1)));
        assertTrue(OffsetDateTime.ofInstant(lastActivity.toInstant(), ZoneOffset.UTC).isBefore(now.plusMinutes(1)));

        now = OffsetDateTime.now();

        // update another file to space with no last modified date
        MockMultipartFile file2
                = new MockMultipartFile(
                "file",
                "valentines2.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Happy Valentines Day Again!".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=some-directory", space1Id.toString()).file(file2)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        result = mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=some-directory", space1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)))
                .andReturn();

        lastActivity = new ObjectMapper().readValue(result.getResponse().getContentAsString(), S3PaginationDto.class).getDocuments().stream()
                .filter(item -> item.getKey().contains("valentines2.txt"))
                .findFirst()
                .get()
                .getLastActivity();

        // not affected by us not providing a last modified date/time
        // check reported last activity is within 1 min (giving some leeway here for JUnit test)
        assertTrue(OffsetDateTime.ofInstant(lastActivity.toInstant(), ZoneOffset.UTC).isAfter(now.minusMinutes(1)));
        assertTrue(OffsetDateTime.ofInstant(lastActivity.toInstant(), ZoneOffset.UTC).isBefore(now.plusMinutes(1)));

        // mark the 2 files as favorites
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/collection/favorite/", space1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(DocumentSpacePathItemsDto.builder()
                    .currentPath("some-directory")
                    .items(Lists.newArrayList("valentines.txt", "valentines2.txt"))
                    .build())))
                .andExpect(status().isCreated());

        // verify
        // make sure that the favorites dump supports/reports this last activity indication as well
        result = mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/collection/favorite", space1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].key", hasItems("valentines.txt", "valentines2.txt")))
                .andReturn();

        lastActivity = new ObjectMapper().readValue(result.getResponse().getContentAsString(), DocumentSpaceUserCollectionResponseDtoWrapper.class).getData()
                .stream()
                .filter(item -> item.getKey().contains("valentines2.txt"))
                .findFirst()
                .get()
                .getLastActivity();

        assertTrue(OffsetDateTime.ofInstant(lastActivity.toInstant(), ZoneOffset.UTC).isAfter(now.minusMinutes(1)));
        assertTrue(OffsetDateTime.ofInstant(lastActivity.toInstant(), ZoneOffset.UTC).isBefore(now.plusMinutes(1)));
    }


    @Test
    @Transactional
    @Rollback
    void testRecentActivityByUserAndSpace() throws Exception {

        // test the endpoints of a user requesting their upload activity across the spaces they have access to
        //  then test the endpoints for checking recent activity according to space (provided they have read access)

        // create 3 spaces that each have 3 files in them
        UUID space1Id = createSpaceWithFiles("space1");
        UUID space2Id = createSpaceWithFiles("space2");
        UUID space3Id = createSpaceWithFiles("space3");

        // grant user write access to spaces 2 and 3
        DashboardUser someUser = DashboardUser.builder()
                .email("someUser@test.gov")
                .privileges(Set.of(
                        privRepo.findByName("DOCUMENT_SPACE_USER").orElseThrow(() -> new RecordNotFoundException("No Document Space User Priv")),
                        privRepo.findByName("DASHBOARD_USER").orElseThrow(() -> new RecordNotFoundException("No DASH_BOARD USER"))
                ))
                .build();

        dashRepo.save(someUser);
        documentSpaceService.addDashboardUserToDocumentSpace(space2Id, DocumentSpaceDashboardMemberRequestDto.builder()
                .email("someUser@test.gov")
                .privileges(Lists.newArrayList(ExternalDocumentSpacePrivilegeType.WRITE))
                .build());
        documentSpaceService.addDashboardUserToDocumentSpace(space3Id, DocumentSpaceDashboardMemberRequestDto.builder()
                .email("someUser@test.gov")
                .privileges(Lists.newArrayList(ExternalDocumentSpacePrivilegeType.WRITE))
                .build());

        // as our user, request recent upload activity for themselves (should be nothing)
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/files/recently-uploaded")
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(someUser.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        // but for admin it should be 9 elements (each of the spaces got 3 files placed there each for us on creation -- by admin)
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/files/recently-uploaded")
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(9)));

        // as user, upload a file to all three spaces
        MockMultipartFile file
                = new MockMultipartFile(
                "file",
                "hello.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Hello, World!".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload", space1Id.toString()).file(file)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(someUser.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload", space2Id.toString()).file(file)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(someUser.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload", space3Id.toString()).file(file)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(someUser.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // as our user, request recent upload activity for themselves (should be 2)
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/files/recently-uploaded")
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(someUser.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));

        // now test activity by space
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/recents", space1Id)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(someUser.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/recents", space2Id)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(someUser.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)));
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/recents", space3Id)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(someUser.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].path", equalTo("/")));

        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/recents", space1Id)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)));
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/recents", space2Id)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)));
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/recents", space3Id)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)));

    }

    @Test
    @Transactional
    @Rollback
    void testUploadFolderThatHasVersionInArchivedState() throws Exception {

        // tests that we can upload a folder of stuff that has a like-named folder
        //  already in the archived state (trash can)

        // test that we can create paths (folders of which) on the fly that don't exist
        UUID spaceId = createSpaceWithFiles("space1");

        // send up the file
        MockMultipartFile file
                = new MockMultipartFile(
                "file",
                "hello-world.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Hello, World!".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=/some/path", spaceId.toString()).file(file)
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // confirm the operation
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/some/path", spaceId.toString())
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(1)))
                .andExpect(jsonPath("$.documents[0].key", equalTo("hello-world.txt")));

        // upload another file to that same location
        MockMultipartFile file2
                = new MockMultipartFile(
                "file",
                "hello-world2.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Hello, World2!".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=/some/path", spaceId.toString()).file(file2)
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // confirm the operation
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/some/path", spaceId.toString())
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)))
                .andExpect(jsonPath("$.documents[*].key", hasItem("hello-world2.txt")));

        // now archive the /some/path folder
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}/archive", spaceId.toString())
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(DocumentSpaceArchiveItemsDto.builder()
                                .currentPath("/")
                                .itemsToArchive(Lists.newArrayList("some"))
                                .build())))
                .andExpect(status().isNoContent());

        // now try to upload a /some/path folder again but with different stuff in it - this should not be allowed since
        // the "some" folder is already archived since this upload request would normally create the path /some/path
        MockMultipartFile file3
                = new MockMultipartFile(
                "file",
                "hello-world3.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Hello, World3!".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=/some/path", spaceId.toString()).file(file3)
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isConflict());
    }

    @Transactional
    @Rollback
    @Test
    void testDocumentSpaceSearch() throws Exception {

        // test that we can search a space

        UUID spaceId = createSpaceWithFiles("space1");

        // send up the file
        MockMultipartFile file
                = new MockMultipartFile(
                "file",
                "hello-world.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Hello, World!".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=/some/path", spaceId.toString()).file(file)
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // confirm the operation
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/some/path", spaceId.toString())
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(1)))
                .andExpect(jsonPath("$.documents[0].key", equalTo("hello-world.txt")));

        // upload another file to that same location
        MockMultipartFile file2
                = new MockMultipartFile(
                "file",
                "hello-world2.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Hello, World2!".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload?path=/some/path", spaceId.toString()).file(file2)
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // search
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/search", spaceId.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(DocumentSpaceSearchDto.builder()
                        .query("world")
                        .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));

        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/search", spaceId.toString())
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(DocumentSpaceSearchDto.builder()
                                .query("world2")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/search", spaceId.toString())
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(DocumentSpaceSearchDto.builder()
                                .query("blah")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/search", spaceId.toString())
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(DocumentSpaceSearchDto.builder()
                                .query("")
                                .build())))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/search", spaceId.toString())
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

    }

    @Test
    @Transactional
    @Rollback
    void testAppClientDocSpaceAccess() throws Exception {

        // runs tests to check if an app client can access the spaces it's allowed to

        UUID space1Id = createSpaceWithFiles("space1");
        UUID space2Id = createSpaceWithFiles("space2");

        // make an app client "app1"
        AppClientUserDto app1 = AppClientUserDto.builder()
                .id(UUID.randomUUID())
                .name("App1")
                .appClientDeveloperEmails(Lists.newArrayList(admin.getEmail()))
                .build();

        MvcResult app1Result = mockMvc.perform(post(APP_CLIENT_ENDPOINT_V2)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(app1)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID app1Id = MAPPER.readValue(app1Result.getResponse().getContentAsString(), AppClientUserDto.class).getId();
        app1.setId(app1Id);

        // make another app client "app2"
        AppClientUserDto app2 = AppClientUserDto.builder()
                .id(UUID.randomUUID())
                .name("App2")
                .appClientDeveloperEmails(Lists.newArrayList(admin.getEmail()))
                .build();

        MvcResult app2Result = mockMvc.perform(post(APP_CLIENT_ENDPOINT_V2)
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(app2)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID app2Id = MAPPER.readValue(app2Result.getResponse().getContentAsString(), AppClientUserDto.class).getId();
        app2.setId(app2Id);

        // try to get files from space1 as app1
        // should fail 403
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/", space1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeader("app1")))
                .andExpect(status().isForbidden());

        // now add app1 to space1 as a reader
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/app-client", space1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(DocumentSpaceAppClientMemberRequestDto.builder()
                        .appClientId(app1Id)
                        .privileges(Lists.newArrayList())
                        .build())))
                .andExpect(status().isNoContent());

        // try to get files from space1 as app1
        // should be 200
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/", space1Id.toString())
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeader("app1")))
                .andExpect(status().isOk());

        // try to upload a file
        // should be 403 since we only have READ access
        MockMultipartFile file
                = new MockMultipartFile(
                "file",
                "hello.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Hello, World!".getBytes()
        );
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload", space1Id.toString()).file(file)
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeader("app1")))
                .andExpect(status().isForbidden());

        // grant WRITE
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/app-client", space1Id.toString())
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(DocumentSpaceAppClientMemberRequestDto.builder()
                                .appClientId(app1Id)
                                .privileges(Lists.newArrayList(ExternalDocumentSpacePrivilegeType.WRITE))
                                .build())))
                .andExpect(status().isNoContent());

        // verify
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/app-clients", space1Id.toString())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].privileges", hasItems("READ", "WRITE")));

        // upload should work now
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload", space1Id.toString()).file(file)
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeader("app2")))
                .andExpect(status().isForbidden());

        // make sure app2 can't read
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/", space1Id.toString())
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeader("app1")))
                .andExpect(status().isOk());

        // make sure app2 can't write
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/app-client", space1Id.toString())
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeader("app2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(DocumentSpaceAppClientMemberRequestDto.builder()
                                .appClientId(app1Id)
                                .privileges(Lists.newArrayList(ExternalDocumentSpacePrivilegeType.WRITE))
                                .build())))
                .andExpect(status().isForbidden());

        // test membership
        // should fail
        mockMvc.perform(post(ENDPOINT_V2 +"/spaces/{id}/users", space1Id.toString())
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeader("app1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(DocumentSpaceDashboardMemberRequestDto.builder()
                                .email("airman@test.mil")
                                .privileges(Lists.newArrayList(ExternalDocumentSpacePrivilegeType.WRITE))
                                .build())))
                .andExpect(status().isForbidden());

        // grant ADMIN privs
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/app-client", space1Id.toString())
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(DocumentSpaceAppClientMemberRequestDto.builder()
                                .appClientId(app1Id)
                                .privileges(Lists.newArrayList(ExternalDocumentSpacePrivilegeType.MEMBERSHIP))
                                .build())))
                .andExpect(status().isNoContent());

        // the app1 can assign people now
        mockMvc.perform(post(ENDPOINT_V2 +"/spaces/{id}/users", space1Id.toString())
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeader("app1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(DocumentSpaceDashboardMemberRequestDto.builder()
                                .email("airman@test.mil")
                                .privileges(Lists.newArrayList(ExternalDocumentSpacePrivilegeType.WRITE))
                                .build())))
                .andExpect(status().isNoContent());

        // check can't cross read into other spaces (not sure why this would be though)....
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/", space2Id.toString())
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeader("app1")))
                .andExpect(status().isForbidden());

        // can still upload
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload", space1Id.toString()).file(file)
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeader("app1")))
                .andExpect(status().isOk());

        // check that doc space has App1 assigned to it with the correct privs
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/app-clients", space1Id.toString())
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1))) // should be just App1
                .andExpect(jsonPath("$.data[0].appClientId", equalTo(app1.getId().toString())))
                .andExpect(jsonPath("$.data[0].appClientName", equalTo("App1")))
                .andExpect(jsonPath("$.data[0].privileges", hasItems("READ", "WRITE", "MEMBERSHIP")));

        // check that doc space only has 1 additional possible app client to choose from
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/available-app-clients", space1Id.toString())
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3))) // should be puckboard, guardian-angel, and App2
                .andExpect(jsonPath("$.data[*].name", hasItem("App2")));

        // now remove privileges
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}/app-client?appClientId={appId}", space1Id.toString(), app1Id.toString())
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isNoContent());

        // can't upload anymore
        mockMvc.perform(multipart(ENDPOINT_V2 + "/spaces/{id}/files/upload", space1Id.toString()).file(file)
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeader("app1")))
                .andExpect(status().isForbidden());

        // cant assign people either
        mockMvc.perform(post(ENDPOINT_V2 +"/spaces/{id}/users", space1Id.toString())
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeader("app1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(DocumentSpaceDashboardMemberRequestDto.builder()
                                .email("airman@test.mil")
                                .privileges(Lists.newArrayList(ExternalDocumentSpacePrivilegeType.WRITE))
                                .build())))
                .andExpect(status().isForbidden());

        // cant read either/access
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/", space1Id.toString())
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeader("app1")))
                .andExpect(status().isForbidden());

        // assign an access (ADMIN) to space #2
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/app-client", space2Id.toString())
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(DocumentSpaceAppClientMemberRequestDto.builder()
                                .appClientId(app1Id)
                                .privileges(Lists.newArrayList(ExternalDocumentSpacePrivilegeType.MEMBERSHIP))
                                .build())))
                .andExpect(status().isNoContent());

        // then delete the doc space to make sure no constraint issues
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}", space2Id.toString())
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isNoContent());

        // delete App Client that's still tied to space1 to make sure constraint issues
        mockMvc.perform(delete(APP_CLIENT_ENDPOINT_V2 + "/{id}", app1Id)
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());
    }

    @Transactional
    @Rollback
    @Test
    void testDigitizeDocSpaceAccess() throws Exception {
        // test that request coming from Digitize app clients can use assigned doc spaces

        UUID space1Id = createSpaceWithFiles("space1");

        // make sure Tron Digitize itself is an app client
        mockMvc.perform(post(APP_CLIENT_ENDPOINT_V2)
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(AppClientUserDto.builder()
                                .name("digitize")
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();

        // predefine scratchUser1
        ScratchStorageUserDto scratchUser1 = ScratchStorageUserDto
                .builder()
                .email("scratch_user1@test.com")
                .build();

        // persist/create scratchUser1
        mockMvc.perform(post("/v2/scratch/users")
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(scratchUser1)))
                .andExpect(status().isCreated());

        // make an digitize app called "app1"
        UUID scratchApp = UUID.randomUUID();
        Map<String, String> app1Record = new HashMap<>();
        app1Record.put("id", scratchApp.toString());
        app1Record.put("appName", "app1");
        mockMvc.perform(post("/v2/scratch/apps")
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(app1Record)))
                .andExpect(status().isCreated());

        // get the scratch privs from the db
        List<PrivilegeDto> privs = Lists.newArrayList(privRepo
            .findAll())
                .stream()
                .map(item -> new ModelMapper().map(item, PrivilegeDto.class))
                .collect(Collectors.toList());

        Long scratchReadPrivId = privs.stream()
                .filter(item -> item.getName().equals("SCRATCH_READ"))
                .collect(Collectors.toList()).get(0).getId();

        // define scratchUser1's privilege (READ) set to app1
        ScratchStorageAppUserPrivDto user1PrivDto =
                ScratchStorageAppUserPrivDto.builder()
                        .email(scratchUser1.getEmail())
                        .privilegeId(scratchReadPrivId)
                        .build();

        mockMvc.perform(patch("/v2/scratch/apps/{appId}/user", scratchApp)
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(user1PrivDto)))
                .andExpect(status().isOk());

        // at this point we just have a standard digitize scratch space set up
        //  the digitize app has NOT been turned into an app client yet, so
        //  shouldn't be able to interact with the document space
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/", space1Id.toString())
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(scratchUser1.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeader("digitize"))
                        .header("digitize-id", scratchApp.toString()))
                .andExpect(status().isForbidden());

        // now make app1 an app client (digitize apps that are app clients have to be named with the
        // prefix of "digitize-")
        AppClientUserDto app1 = AppClientUserDto.builder()
                .id(UUID.randomUUID())
                .name("digitize-app1")
                .appClientDeveloperEmails(Lists.newArrayList(admin.getEmail()))
                .build();

        MvcResult app1Result = mockMvc.perform(post(APP_CLIENT_ENDPOINT_V2)
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(app1)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID app1Id = MAPPER.readValue(app1Result.getResponse().getContentAsString(), AppClientUserDto.class).getId();
        app1.setId(app1Id);

        // now try to read again - should still be 403
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/", space1Id.toString())
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(scratchUser1.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeader("digitize"))
                        .header("digitize-id", scratchApp.toString()))
                .andExpect(status().isForbidden());

        // add "digitize-app1" to app clients allowed to access (READ) from space1
        mockMvc.perform(post(ENDPOINT_V2 + "/spaces/{id}/app-client", space1Id.toString())
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(DocumentSpaceAppClientMemberRequestDto.builder()
                                .appClientId(app1Id)
                                .privileges(Lists.newArrayList())
                                .build())))
                .andExpect(status().isNoContent());

        // now try to read again - should be 200
        mockMvc.perform(get(ENDPOINT_V2 + "/spaces/{id}/contents?path=/", space1Id.toString())
                        .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(scratchUser1.getEmail()))
                        .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeader("digitize"))
                        .header("digitize-id", scratchApp.toString()))
                .andExpect(status().isOk());
    }
}
