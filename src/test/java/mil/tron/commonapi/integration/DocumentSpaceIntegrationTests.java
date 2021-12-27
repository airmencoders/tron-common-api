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
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
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
        mockMvc.perform(delete(ENDPOINT_V2 + "/spaces/{id}/archived/delete", test1Id.toString())
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

    // Doc Space Mobile aggregation endpoints

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
}
