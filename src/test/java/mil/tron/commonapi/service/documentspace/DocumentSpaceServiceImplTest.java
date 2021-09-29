package mil.tron.commonapi.service.documentspace;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;

import io.findify.s3mock.S3Mock;
import mil.tron.commonapi.dto.documentspace.DocumentDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceInfoDto;
import mil.tron.commonapi.dto.documentspace.S3PaginationDto;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ExtendWith(MockitoExtension.class)
public class DocumentSpaceServiceImplTest {
	private static final String BUCKET_NAME = "testbucket";
	
    private DocumentSpaceServiceImpl documentService;
    private AmazonS3 amazonS3;
    private TransferManager transferManager;
    private S3Mock s3Mock;
    private DocumentSpaceInfoDto dto = DocumentSpaceInfoDto.builder()
            .name("test")
            .build();

    private DocumentSpaceInfoDto invalidDto = DocumentSpaceInfoDto.builder()
            .name("test name")
            .build();

    @BeforeEach
    void setup() {
        BasicAWSCredentials awsCredentials = new BasicAWSCredentials("admin", "admin");
        ClientConfiguration clientConfiguration = new ClientConfiguration();

        amazonS3 = AmazonS3ClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("http://localhost:9002", "Earth"))
                .withPathStyleAccessEnabled(true)
                .withClientConfiguration(clientConfiguration)
                .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
                .build();
        
        transferManager = TransferManagerBuilder.standard().withS3Client(amazonS3).build();

        documentService = new DocumentSpaceServiceImpl(amazonS3, transferManager, BUCKET_NAME);

        s3Mock = new S3Mock.Builder()
                .withPort(9002)
                .withInMemoryBackend()
                .build();

        s3Mock.start();
        amazonS3.createBucket(BUCKET_NAME);
    }

    @AfterEach
    void destroy() {
        s3Mock.shutdown();
    }

    @Test
    void testListSpaces() {
        assertEquals(0, documentService.listSpaces().size());
        documentService.createSpace(dto);
        assertEquals(1, documentService.listSpaces().size());

        // test invalid name caught
        assertThrows(BadRequestException.class, () -> documentService.createSpace(invalidDto));

        // test duplicate name caught
        assertThrows(ResourceAlreadyExistsException.class, () -> documentService.createSpace(dto));
    }

    @Test
    void deleteSpace() {
        documentService.createSpace(dto);
        assertEquals(1, documentService.listSpaces().size());
        assertDoesNotThrow(() -> documentService.deleteSpace("test"));
        assertThrows(RecordNotFoundException.class, () -> documentService.deleteSpace("test"));
    }
    
    @Test
    void testListFiles() {
    	documentService.createSpace(dto);
    	List<String> fileNames = new ArrayList<>();
    	String fakeContent = "fake content";
    	for (int i = 0; i < 20; i++) {
    		String filename = String.format("file%d.txt", i);
    		fileNames.add(filename);
    		
    		MockMultipartFile file = new MockMultipartFile(filename, filename, "multipart/form-data", fakeContent.getBytes()); 
        	documentService.uploadFile(dto.getName(), file);
    	}
    	
    	S3PaginationDto s3PaginationDto = documentService.listFiles(dto.getName(), null, 30);
    	
    	assertThat(s3PaginationDto.getSize()).isEqualTo(30);
    	assertThat(s3PaginationDto.getTotalElements()).isEqualTo(fileNames.size());
    	assertThat(s3PaginationDto.getDocuments())
    		.usingRecursiveComparison()
    		.ignoringCollectionOrder()
    		.ignoringFields("uploadedDate", "uploadedBy")
    		.isEqualTo(fileNames.stream().map(filename -> DocumentDto.builder()
    				.key(filename)
    				.path(dto.getName())
    				.size(fakeContent.getBytes().length)
    				.build()).collect(Collectors.toList()));
    }
    
    @Test
    void testDownloadFile() {
    	documentService.createSpace(dto);
    	String filename = String.format("file%d.txt", 1);
    	MockMultipartFile file = new MockMultipartFile(filename, filename, "multipart/form-data", "fake content".getBytes()); 
    	documentService.uploadFile(dto.getName(), file);

    	S3Object downloadFile = documentService.downloadFile(dto.getName(), filename);
    	S3Object actual = amazonS3.getObject(BUCKET_NAME, dto.getName() + "/" + filename);
    	assertThat(downloadFile.getKey()).isEqualTo(actual.getKey());
    }
    
    @Test
    void testGetFiles() {
    	documentService.createSpace(dto);
    	
    	List<String> fileNames = new ArrayList<>();
    	for (int i = 0; i < 5; i++) {
    		String filename = String.format("file%d.txt", i);
    		fileNames.add(filename);
    		
    		MockMultipartFile file = new MockMultipartFile(filename, filename, "multipart/form-data", "fake content".getBytes()); 
        	documentService.uploadFile(dto.getName(), file);
    	}
    	
    	List<S3Object> downloadFiles = documentService.getFiles(dto.getName(), Set.copyOf(fileNames));
    	
    	List<S3Object> fromS3 = new ArrayList<>();
    	for (int i = 0; i < fileNames.size(); i++) {
    		fromS3.add(amazonS3.getObject(BUCKET_NAME, dto.getName() + "/" + fileNames.get(i)));
    	}
    	
    	assertThat(downloadFiles).hasSameSizeAs(fromS3);
    	for (int i = 0; i < downloadFiles.size(); i++) {
    		String key = downloadFiles.get(i).getKey();
    		
    		assertThat(fromS3).anyMatch(actual -> actual.getKey().equals(key));
    	}
    }
    
    @Test
    void testDownloadAndWriteCompressedFiles() {
    	documentService.createSpace(dto);
    	List<String> fileNames = new ArrayList<>();
    	for (int i = 0; i < 5; i++) {
    		String filename = String.format("file%d.txt", i);
    		fileNames.add(filename);
    		
    		MockMultipartFile file = new MockMultipartFile(filename, filename, "multipart/form-data", "fake content".getBytes()); 
        	documentService.uploadFile(dto.getName(), file);
    	}
    	
    	ByteArrayOutputStream output = new ByteArrayOutputStream();
    	
    	documentService.downloadAndWriteCompressedFiles(dto.getName(), Set.copyOf(fileNames), output);
    	
    	assertThat(output.size()).isPositive();
    }
    
    @Test
    void testdownloadAllInDirectoryAndCompress() {
    	documentService.createSpace(dto);
    	List<String> fileNames = new ArrayList<>();
    	for (int i = 0; i < 5; i++) {
    		String filename = String.format("file%d.txt", i);
    		fileNames.add(filename);
    		
    		MockMultipartFile file = new MockMultipartFile(filename, filename, "multipart/form-data", "fake content".getBytes()); 
        	documentService.uploadFile(dto.getName(), file);
    	}
    	
    	ByteArrayOutputStream output = new ByteArrayOutputStream();
    	
    	documentService.downloadAllInSpaceAndCompress(dto.getName(), output);
    	
    	assertThat(output.size()).isPositive();
    }
}
