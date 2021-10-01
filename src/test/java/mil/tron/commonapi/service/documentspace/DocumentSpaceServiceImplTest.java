package mil.tron.commonapi.service.documentspace;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;

import io.findify.s3mock.S3Mock;
import mil.tron.commonapi.dto.documentspace.DocumentDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceResponseDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceRequestDto;
import mil.tron.commonapi.dto.documentspace.S3PaginationDto;
import mil.tron.commonapi.entity.documentspace.DocumentSpace;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ExtendWith(MockitoExtension.class)
class DocumentSpaceServiceImplTest {
	private static final String BUCKET_NAME = "testbucket";
	
    private DocumentSpaceServiceImpl documentService;
    private AmazonS3 amazonS3;
    private TransferManager transferManager;
    
    @Mock
    private DocumentSpaceRepository documentSpaceRepo;
    
    private S3Mock s3Mock;
    
    private DocumentSpaceRequestDto requestDto;
    private DocumentSpaceResponseDto responseDto;
    private DocumentSpace entity;
    
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

        documentService = new DocumentSpaceServiceImpl(amazonS3, transferManager, BUCKET_NAME, documentSpaceRepo);

        s3Mock = new S3Mock.Builder()
                .withPort(9002)
                .withInMemoryBackend()
                .build();

        s3Mock.start();
        amazonS3.createBucket(BUCKET_NAME);
        
        requestDto = DocumentSpaceRequestDto.builder()
        		.id(UUID.randomUUID())
                .name("test")
                .build();
        
        responseDto = DocumentSpaceResponseDto.builder()
        		.id(requestDto.getId())
        		.name(requestDto.getName())
        		.build();
        
        entity = DocumentSpace.builder()
        			.id(requestDto.getId())
	        		.name(requestDto.getName())
	        		.build();
    }

    @AfterEach
    void destroy() {
        s3Mock.shutdown();
    }
    
	List<String> uploadDummyFilesUsingTransferManager(String content, int numOfFiles)
			throws AmazonServiceException, AmazonClientException, InterruptedException {
		List<String> fileNames = new ArrayList<>();
		String fakeContent = content;
		for (int i = 0; i < numOfFiles; i++) {
			String filename = String.format("file%d.txt", i);
			fileNames.add(filename);

			ObjectMetadata metaData = new ObjectMetadata();
			metaData.setContentType("text");
			metaData.setContentLength(fakeContent.getBytes().length);

			amazonS3.putObject(BUCKET_NAME, documentService.createDocumentSpacePathPrefix(entity.getId()) + filename,
					new ByteArrayInputStream(fakeContent.getBytes()), metaData);
		}

		return fileNames;
	}

    @Test
    void testListSpaces() {
    	Mockito.when(documentSpaceRepo.findAllDynamicBy(DocumentSpaceResponseDto.class)).thenReturn(List.of());
        assertEquals(0, documentService.listSpaces().size());
        
        Mockito.when(documentSpaceRepo.findAllDynamicBy(DocumentSpaceResponseDto.class)).thenReturn(List.of(responseDto));
        assertEquals(1, documentService.listSpaces().size());
    }
    
    @Test
    void testUploadFile() {
    	String fakeContent = "fake content";
		MockMultipartFile file = new MockMultipartFile("filename.txt", "filename.txt", "multipart/form-data", fakeContent.getBytes()); 
		
		Mockito.when(documentSpaceRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(entity));
    	documentService.uploadFile(entity.getId(), file);
    	
    	assertThat(amazonS3.doesObjectExist(BUCKET_NAME, documentService.createDocumentSpacePathPrefix(entity.getId()) + "filename.txt")).isTrue();
    }

    @Test
    void deleteSpace() {
        Mockito.when(documentSpaceRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(entity));
        assertDoesNotThrow(() -> documentService.deleteSpace(entity.getId()));
        
        Mockito.when(documentSpaceRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.ofNullable(null));
        assertThrows(RecordNotFoundException.class, () -> documentService.deleteSpace(entity.getId()));
    }
    
	@Test
	void testDeleteFile() throws AmazonServiceException, AmazonClientException, InterruptedException {
		String content = "fake content";
    	List<String> fileNames = uploadDummyFilesUsingTransferManager(content, 1);
		
		assertThat(amazonS3.doesObjectExist(BUCKET_NAME, documentService.createDocumentSpacePathPrefix(entity.getId()) + fileNames.get(0))).isTrue();

		Mockito.when(documentSpaceRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(entity));
		documentService.deleteFile(entity.getId(), fileNames.get(0));
		
		assertThat(amazonS3.doesObjectExist(BUCKET_NAME, documentService.createDocumentSpacePathPrefix(entity.getId()) + fileNames.get(0))).isFalse();
	}
    
    @Test
    void testListFiles() throws AmazonServiceException, AmazonClientException, InterruptedException {
    	Mockito.when(documentSpaceRepo.save(Mockito.any(DocumentSpace.class))).thenReturn(entity);
    	DocumentSpaceResponseDto documentSpaceDto = documentService.createSpace(requestDto);
    	
    	String content = "fake content";
    	List<String> fileNames = uploadDummyFilesUsingTransferManager(content, 20);
    	
    	Mockito.when(documentSpaceRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(entity));
    	S3PaginationDto s3PaginationDto = documentService.listFiles(documentSpaceDto.getId(), null, 30);
    	
    	assertThat(s3PaginationDto.getSize()).isEqualTo(30);
    	assertThat(s3PaginationDto.getTotalElements()).isEqualTo(fileNames.size());
    	assertThat(s3PaginationDto.getDocuments())
    		.usingRecursiveComparison()
    		.ignoringCollectionOrder()
    		.ignoringFields("uploadedDate", "uploadedBy")
    		.isEqualTo(fileNames.stream().map(filename -> DocumentDto.builder()
    				.key(filename)
    				.path(requestDto.getId() + "/")
    				.size(content.getBytes().length)
    				.build()).collect(Collectors.toList()));
    }
    
    @Test
    void testDownloadFile() throws AmazonServiceException, AmazonClientException, InterruptedException {
    	Mockito.when(documentSpaceRepo.save(Mockito.any(DocumentSpace.class))).thenReturn(entity);
    	DocumentSpaceResponseDto documentSpaceDto = documentService.createSpace(requestDto);
    	
    	String content = "fake content";
    	List<String> fileNames = uploadDummyFilesUsingTransferManager(content, 20);

    	Mockito.when(documentSpaceRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(entity));
    	S3Object downloadFile = documentService.downloadFile(documentSpaceDto.getId(), fileNames.get(0));
    	
    	S3Object actual = amazonS3.getObject(BUCKET_NAME, documentService.createDocumentSpacePathPrefix(entity.getId()) + fileNames.get(0));
    	assertThat(downloadFile.getKey()).isEqualTo(actual.getKey());
    }
    
    @Test
    void testGetFiles() throws AmazonServiceException, AmazonClientException, InterruptedException {
    	Mockito.when(documentSpaceRepo.save(Mockito.any(DocumentSpace.class))).thenReturn(entity);
    	DocumentSpaceResponseDto documentSpaceDto = documentService.createSpace(requestDto);
    	
    	String content = "fake content";
    	List<String> fileNames = uploadDummyFilesUsingTransferManager(content, 5);
    	
    	Mockito.when(documentSpaceRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(entity));
    	List<S3Object> downloadFiles = documentService.getFiles(documentSpaceDto.getId(), Set.copyOf(fileNames));
    	
    	List<S3Object> fromS3 = new ArrayList<>();
    	for (int i = 0; i < fileNames.size(); i++) {
    		fromS3.add(amazonS3.getObject(BUCKET_NAME, documentService.createDocumentSpacePathPrefix(entity.getId()) + fileNames.get(i)));
    	}
    	
    	assertThat(downloadFiles).hasSameSizeAs(fromS3);
    	for (int i = 0; i < downloadFiles.size(); i++) {
    		String key = downloadFiles.get(i).getKey();
    		
    		assertThat(fromS3).anyMatch(actual -> actual.getKey().equals(key));
    	}
    }
    
    @Test
    void testDownloadAndWriteCompressedFiles() throws AmazonServiceException, AmazonClientException, InterruptedException {
    	Mockito.when(documentSpaceRepo.save(Mockito.any(DocumentSpace.class))).thenReturn(entity);
    	DocumentSpaceResponseDto documentSpaceDto = documentService.createSpace(requestDto);
    	
    	String content = "fake content";
    	List<String> fileNames = uploadDummyFilesUsingTransferManager(content, 5);
    	
    	ByteArrayOutputStream output = new ByteArrayOutputStream();
    	
    	Mockito.when(documentSpaceRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(entity));
    	documentService.downloadAndWriteCompressedFiles(documentSpaceDto.getId(), Set.copyOf(fileNames), output);
    	
    	assertThat(output.size()).isPositive();
    }
    
    @Test
    void testdownloadAllInDirectoryAndCompress() throws AmazonServiceException, AmazonClientException, InterruptedException {
    	Mockito.when(documentSpaceRepo.save(Mockito.any(DocumentSpace.class))).thenReturn(entity);
    	DocumentSpaceResponseDto documentSpaceDto = documentService.createSpace(requestDto);
    	
    	String content = "fake content";
    	uploadDummyFilesUsingTransferManager(content, 5);
    	
    	ByteArrayOutputStream output = new ByteArrayOutputStream();
    	
    	Mockito.when(documentSpaceRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(entity));
    	documentService.downloadAllInSpaceAndCompress(documentSpaceDto.getId(), output);
    	
    	assertThat(output.size()).isPositive();
    }
}
