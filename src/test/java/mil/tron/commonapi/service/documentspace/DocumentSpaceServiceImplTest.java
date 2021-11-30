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
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import io.findify.s3mock.S3Mock;
import mil.tron.commonapi.dto.documentspace.*;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.documentspace.DocumentSpace;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceDashboardMemberPrivilegeRow;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;
import mil.tron.commonapi.entity.documentspace.DocumentSpacePrivilege;
import mil.tron.commonapi.exception.NotAuthorizedException;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceRepository;
import mil.tron.commonapi.service.DashboardUserService;
import mil.tron.commonapi.service.documentspace.util.FilePathSpec;
import mil.tron.commonapi.service.documentspace.util.FilePathSpecWithContents;
import mil.tron.commonapi.service.documentspace.util.FileSystemElementTree;
import mil.tron.commonapi.service.documentspace.util.S3ObjectAndFilename;

import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.util.Lists;
import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.domain.Sort.Order;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class DocumentSpaceServiceImplTest {
	private static final String BUCKET_NAME = "testbucket";

	@InjectMocks
	private DocumentSpaceServiceImpl documentService;

	private AmazonS3 amazonS3;
	private TransferManager transferManager;

	@Mock
	private DocumentSpaceRepository documentSpaceRepo;

	@Mock
	private DashboardUserRepository dashboardUserRepository;

	@Mock
	private DocumentSpacePrivilegeService documentSpacePrivilegeService;

	@Mock
	private DocumentSpaceFileSystemService documentSpaceFileSystemService;

	@Mock
	private DashboardUserService dashboardUserService;

	@Mock
	private PrivilegeRepository privilegeRepository;
	
	@Mock
	private DocumentSpaceFileService documentSpaceFileService;
	
	@Mock
	private DocumentSpaceMetadataService metadataService;

	private S3Mock s3Mock;

	private DocumentSpaceRequestDto requestDto;
	private DocumentSpaceResponseDto responseDto;
	private DocumentSpace entity;
	private DashboardUser dashboardUser;

	@BeforeEach
	void setup() {
		BasicAWSCredentials awsCredentials = new BasicAWSCredentials("admin", "admin");
		ClientConfiguration clientConfiguration = new ClientConfiguration();

		amazonS3 = AmazonS3ClientBuilder.standard()
				.withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("http://localhost:9002", "Earth"))
				.withPathStyleAccessEnabled(true).withClientConfiguration(clientConfiguration)
				.withCredentials(new AWSStaticCredentialsProvider(awsCredentials)).build();

		transferManager = TransferManagerBuilder.standard().withS3Client(amazonS3).build();

		documentService = new DocumentSpaceServiceImpl(amazonS3, transferManager, BUCKET_NAME, documentSpaceRepo,
				documentSpacePrivilegeService, dashboardUserRepository, dashboardUserService, privilegeRepository,
				documentSpaceFileSystemService, documentSpaceFileService, metadataService);
		s3Mock = new S3Mock.Builder().withPort(9002).withInMemoryBackend().build();

		s3Mock.start();
		amazonS3.createBucket(BUCKET_NAME);

		requestDto = DocumentSpaceRequestDto.builder().id(UUID.randomUUID()).name("test").build();

		responseDto = DocumentSpaceResponseDto.builder().id(requestDto.getId()).name(requestDto.getName()).build();

		EnumMap<DocumentSpacePrivilegeType, DocumentSpacePrivilege> documentSpacePrivilegesMap = new EnumMap<>(
				DocumentSpacePrivilegeType.class);
		documentSpacePrivilegesMap.put(DocumentSpacePrivilegeType.READ,
				DocumentSpacePrivilege.builder().id(UUID.randomUUID())
						.name(String.format("DOCUMENT_SPACE_%s_%s", requestDto.getId().toString(), DocumentSpacePrivilegeType.READ))
						.type(DocumentSpacePrivilegeType.READ).build());

		documentSpacePrivilegesMap.put(DocumentSpacePrivilegeType.WRITE,
				DocumentSpacePrivilege.builder().id(UUID.randomUUID())
						.name(String.format("DOCUMENT_SPACE_%s_%s", requestDto.getId().toString(), DocumentSpacePrivilegeType.WRITE))
						.type(DocumentSpacePrivilegeType.WRITE).build());

		documentSpacePrivilegesMap.put(DocumentSpacePrivilegeType.MEMBERSHIP,
				DocumentSpacePrivilege.builder().id(UUID.randomUUID())
						.name(String.format("DOCUMENT_SPACE_%s_%s", requestDto.getId().toString(), DocumentSpacePrivilegeType.MEMBERSHIP))
						.type(DocumentSpacePrivilegeType.MEMBERSHIP).build());
		entity = DocumentSpace.builder().id(requestDto.getId()).name(requestDto.getName())
				.privileges(documentSpacePrivilegesMap).build();
		
		dashboardUser = DashboardUser.builder()
				.id(UUID.randomUUID())
				.email("dashboard@user.com")
				.emailAsLower("dashboard@user.com")
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
		// Test Dashboard Admin should get all
		Mockito.when(dashboardUserService.getDashboardUserByEmailAsLower(Mockito.anyString())).thenReturn(dashboardUser);
		dashboardUser.setPrivileges(Set.of(new Privilege(1L, "DASHBOARD_ADMIN")));
		Mockito.when(documentSpaceRepo.findAllDynamicBy(DocumentSpaceResponseDto.class)).thenReturn(List.of(responseDto));
		assertThat(documentService.listSpaces(dashboardUser.getEmail())).hasSize(1);

		// Test that non-Dashboard Admin should only get by their id
		Mockito.when(dashboardUserService.getDashboardUserByEmailAsLower(Mockito.anyString())).thenReturn(dashboardUser);
		dashboardUser.setPrivileges(Set.of());
		Mockito.when(documentSpaceRepo.findAllDynamicByDashboardUsers_Id(dashboardUser.getId(), DocumentSpaceResponseDto.class)).thenReturn(List.of(responseDto));
		assertThat(documentService.listSpaces(dashboardUser.getEmail())).hasSize(1);

		// Test for exception when Dashboard User not found
		Mockito.when(dashboardUserService.getDashboardUserByEmailAsLower(Mockito.anyString())).thenReturn(null);
		assertThrows(RecordNotFoundException.class, () -> documentService.listSpaces(dashboardUser.getEmail()));
	}

	@Test
	void testUploadFile() {
		String fakeContent = "fake content";
		MockMultipartFile file = new MockMultipartFile("filename.txt", "filename.txt", "multipart/form-data",
				fakeContent.getBytes());

		Mockito.when(documentSpaceRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(entity));
		Mockito.when(
				documentSpaceFileSystemService.parsePathToFilePathSpec(Mockito.any(UUID.class), Mockito.anyString()))
				.thenReturn(
						FilePathSpec.builder().itemId(DocumentSpaceFileSystemEntry.NIL_UUID).build());
		Mockito.when(documentSpaceFileService.getFileInDocumentSpaceFolder(Mockito.any(), Mockito.any(),
				Mockito.anyString())).thenReturn(Optional.ofNullable(null));
		documentService.uploadFile(entity.getId(), "", file);

		assertThat(amazonS3.doesObjectExist(BUCKET_NAME,
				documentService.createDocumentSpacePathPrefix(entity.getId()) + "filename.txt")).isTrue();
		Mockito.verify(documentSpaceFileService).saveDocumentSpaceFile(Mockito.any(DocumentSpaceFileSystemEntry.class));
		Mockito.verify(documentSpaceFileSystemService).propagateModificationStateToAncestors(Mockito.any(DocumentSpaceFileSystemEntry.class));
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

		assertThat(amazonS3.doesObjectExist(BUCKET_NAME,
				documentService.createDocumentSpacePathPrefix(entity.getId()) + fileNames.get(0))).isTrue();

		Mockito.when(documentSpaceRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(entity));
		Mockito.when(
				documentSpaceFileSystemService.parsePathToFilePathSpec(Mockito.any(UUID.class), Mockito.anyString()))
				.thenReturn(
						FilePathSpec.builder().itemId(DocumentSpaceFileSystemEntry.NIL_UUID).build());
		Mockito.when(documentSpaceFileService.getFileInDocumentSpaceFolder(Mockito.any(UUID.class),
				Mockito.any(UUID.class), Mockito.anyString()))
				.thenReturn(Optional.of(DocumentSpaceFileSystemEntry.builder().documentSpaceId(entity.getId())
						.isFolder(false).itemName(fileNames.get(0)).build()));
		documentService.deleteFile(entity.getId(), "", fileNames.get(0));

		Mockito.verify(documentSpaceFileService).deleteDocumentSpaceFile(Mockito.any());
		assertThat(amazonS3.doesObjectExist(BUCKET_NAME,
				documentService.createDocumentSpacePathPrefix(entity.getId()) + fileNames.get(0))).isFalse();
		
		Mockito.verify(documentSpaceFileSystemService).propagateModificationStateToAncestors(Mockito.any(DocumentSpaceFileSystemEntry.class));
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
		assertThat(s3PaginationDto.getDocuments()).usingRecursiveComparison().ignoringCollectionOrder()
				.ignoringFields("lastModifiedDate", "lastModifiedBy")
				.isEqualTo(fileNames.stream().map(filename -> DocumentDto.builder().key(filename).path(requestDto.getId() + "/")
						.spaceId(requestDto.getId().toString())
						.size(content.getBytes().length).build()).collect(Collectors.toList()));
	}

	@Test
	void testDownloadFile() throws AmazonServiceException, AmazonClientException, InterruptedException {
		Mockito.when(documentSpaceRepo.save(Mockito.any(DocumentSpace.class))).thenReturn(entity);
		DocumentSpaceResponseDto documentSpaceDto = documentService.createSpace(requestDto);

		String content = "fake content";
		List<String> fileNames = uploadDummyFilesUsingTransferManager(content, 20);

		Mockito.when(dashboardUserService.getDashboardUserByEmailAsLower(Mockito.anyString())).thenReturn(dashboardUser);
		
		Mockito.when(
				documentSpaceFileSystemService.parsePathToFilePathSpec(Mockito.any(UUID.class), Mockito.anyString()))
				.thenReturn(
						FilePathSpec.builder().itemId(DocumentSpaceFileSystemEntry.NIL_UUID).fullPathSpec("").build());
		
		Mockito.when(documentSpaceFileService.getFileInDocumentSpaceFolderOrThrow(Mockito.any(UUID.class), Mockito.any(UUID.class), Mockito.anyString()))
			.thenReturn(DocumentSpaceFileSystemEntry.builder()
					.itemName(fileNames.get(0))
					.build());
		
		S3Object downloadFile = documentService.getFile(documentSpaceDto.getId(), "", fileNames.get(0), dashboardUser.getEmail());

		S3Object actual = amazonS3.getObject(BUCKET_NAME,
				documentService.createDocumentSpacePathPrefix(entity.getId()) + fileNames.get(0));
		assertThat(downloadFile.getKey()).isEqualTo(actual.getKey());
		Mockito.verify(metadataService).saveMetadata(Mockito.any(UUID.class), Mockito.any(DocumentSpaceFileSystemEntry.class), Mockito.any(DocumentMetadata.class), Mockito.any(DashboardUser.class));
	}

	@Test
	void testGetFiles() throws AmazonServiceException, AmazonClientException, InterruptedException {
		Mockito.when(documentSpaceRepo.save(Mockito.any(DocumentSpace.class))).thenReturn(entity);
		DocumentSpaceResponseDto documentSpaceDto = documentService.createSpace(requestDto);

		String content = "fake content";
		List<String> fileNames = uploadDummyFilesUsingTransferManager(content, 5);

		Mockito.when(dashboardUserService.getDashboardUserByEmailAsLower(Mockito.anyString())).thenReturn(dashboardUser);
		
		Mockito.when(
				documentSpaceFileSystemService.parsePathToFilePathSpec(Mockito.any(UUID.class), Mockito.anyString()))
				.thenReturn(
						FilePathSpec.builder().itemId(DocumentSpaceFileSystemEntry.NIL_UUID).fullPathSpec("").build());
		List<S3Object> downloadFiles = documentService.getFiles(documentSpaceDto.getId(), "", Set.copyOf(fileNames), dashboardUser.getEmail());

		List<S3Object> fromS3 = new ArrayList<>();
		for (int i = 0; i < fileNames.size(); i++) {
			fromS3.add(amazonS3.getObject(BUCKET_NAME,
					documentService.createDocumentSpacePathPrefix(entity.getId()) + fileNames.get(i)));
		}

		assertThat(downloadFiles).hasSameSizeAs(fromS3);
		for (int i = 0; i < downloadFiles.size(); i++) {
			String key = downloadFiles.get(i).getKey();

			assertThat(fromS3).anyMatch(actual -> actual.getKey().equals(key));
		}
		Mockito.verify(metadataService).saveMetadata(Mockito.any(UUID.class), Mockito.anySet(), Mockito.any(DocumentMetadata.class), Mockito.any(DashboardUser.class));
	}

	@Test
	void testGetFileBySpaceAndParent() throws AmazonServiceException, AmazonClientException, InterruptedException {
		Mockito.when(documentSpaceRepo.save(Mockito.any(DocumentSpace.class))).thenReturn(entity);
		DocumentSpaceResponseDto documentSpaceDto = documentService.createSpace(requestDto);

		String content = "fake content";
		List<String> fileNames = uploadDummyFilesUsingTransferManager(content, 1);

		Mockito.when(dashboardUserService.getDashboardUserByEmailAsLower(Mockito.anyString())).thenReturn(dashboardUser);
		
		Mockito.when(
				documentSpaceFileSystemService.getFilePathSpec(Mockito.any(UUID.class), Mockito.any(UUID.class)))
				.thenReturn(
						FilePathSpec.builder().itemId(DocumentSpaceFileSystemEntry.NIL_UUID).build());
		
		Mockito.when(documentSpaceFileService.getFileInDocumentSpaceFolderOrThrow(Mockito.any(UUID.class), Mockito.any(UUID.class), Mockito.anyString()))
				.thenReturn(DocumentSpaceFileSystemEntry.builder()
					.itemName(fileNames.get(0))
					.build());
		
		S3Object downloadFile = documentService.getFile(documentSpaceDto.getId(), DocumentSpaceFileSystemEntry.NIL_UUID, fileNames.get(0), dashboardUser.getEmail());

		S3Object s3Object = amazonS3.getObject(BUCKET_NAME, documentService.createDocumentSpacePathPrefix(entity.getId()) + fileNames.get(0));

		assertThat(s3Object.getKey()).isEqualTo(downloadFile.getKey());
		Mockito.verify(metadataService).saveMetadata(Mockito.any(UUID.class), Mockito.any(DocumentSpaceFileSystemEntry.class), Mockito.any(DocumentMetadata.class), Mockito.any(DashboardUser.class));
	}
	
	@Test
	void testDeleteFileBySpaceAndParent() throws AmazonServiceException, AmazonClientException, InterruptedException {
		String content = "fake content";
		List<String> fileNames = uploadDummyFilesUsingTransferManager(content, 1);

		assertThat(amazonS3.doesObjectExist(BUCKET_NAME,
				documentService.createDocumentSpacePathPrefix(entity.getId()) + fileNames.get(0))).isTrue();

		Mockito.when(
				documentSpaceFileSystemService.getFilePathSpec(Mockito.any(UUID.class), Mockito.any(UUID.class)))
				.thenReturn(
						FilePathSpec.builder().itemId(DocumentSpaceFileSystemEntry.NIL_UUID).build());
		
		Mockito.when(documentSpaceFileService.getFileInDocumentSpaceFolder(Mockito.any(UUID.class),
				Mockito.any(UUID.class), Mockito.anyString()))
				.thenReturn(Optional.of(DocumentSpaceFileSystemEntry.builder().documentSpaceId(entity.getId())
						.isFolder(false).itemName(fileNames.get(0)).build()));
		
		documentService.deleteFile(entity.getId(), DocumentSpaceFileSystemEntry.NIL_UUID, fileNames.get(0));

		Mockito.verify(documentSpaceFileService).deleteDocumentSpaceFile(Mockito.any());
		assertThat(amazonS3.doesObjectExist(BUCKET_NAME,
				documentService.createDocumentSpacePathPrefix(entity.getId()) + fileNames.get(0))).isFalse();
		
		Mockito.verify(documentSpaceFileSystemService).propagateModificationStateToAncestors(Mockito.any(DocumentSpaceFileSystemEntry.class));
	}

	@Test
	void testDownloadAndWriteCompressedFiles()
			throws AmazonServiceException, AmazonClientException, InterruptedException {
		Mockito.when(documentSpaceRepo.existsByName(Mockito.anyString())).thenReturn(false);
		Mockito.when(documentSpaceRepo.save(Mockito.any(DocumentSpace.class))).thenReturn(entity);
		DocumentSpaceResponseDto documentSpaceDto = documentService.createSpace(requestDto);

		S3ObjectSummary obj0 = new S3ObjectSummary();
		obj0.setKey(documentService.createDocumentSpacePathPrefix(entity.getId()) + "file0.txt");
		S3ObjectSummary obj1 = new S3ObjectSummary();
		obj1.setKey(documentService.createDocumentSpacePathPrefix(entity.getId()) + "file1.txt");
		String content = "fake content";
		List<String> fileNames = uploadDummyFilesUsingTransferManager(content, 5);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		
		List<DocumentSpaceFileSystemEntry> entries = new ArrayList<>();
		for (String filename : fileNames) {
			entries.add(DocumentSpaceFileSystemEntry.builder()
										.documentSpaceId(entity.getId())
										.isFolder(false)
										.itemId(UUID.randomUUID())
										.itemName(filename)
										.build());
		}
		fileNames.add("folder");
		entries.add(DocumentSpaceFileSystemEntry.builder()
										.documentSpaceId(entity.getId())
										.isFolder(true)
										.itemId(UUID.randomUUID())
										.itemName("folder")
										.build());
		
		Mockito.when(dashboardUserService.getDashboardUserByEmailAsLower(Mockito.anyString())).thenReturn(dashboardUser);
		
		Mockito.when(
				documentSpaceFileSystemService.getFilesAndFoldersAtPath(Mockito.any(UUID.class), Mockito.anyString()))
				.thenReturn(
						FilePathSpecWithContents.builder()
							.itemId(DocumentSpaceFileSystemEntry.NIL_UUID)
							.fullPathSpec("")
							.entries(entries)
							.build()
				);
		
		Mockito.when(
				documentSpaceFileSystemService.parsePathToFilePathSpec(Mockito.any(UUID.class), Mockito.anyString()))
				.thenReturn(
						FilePathSpec.builder().itemId(DocumentSpaceFileSystemEntry.NIL_UUID).fullPathSpec("").build());
		
		Mockito.when(documentSpaceFileSystemService.getFilePathSpec(Mockito.any(UUID.class), Mockito.any(UUID.class)))
			.thenReturn(FilePathSpec.builder().itemId(DocumentSpaceFileSystemEntry.NIL_UUID).fullPathSpec("/folder").build());
		
		Mockito.when(documentSpaceFileSystemService.dumpElementTree(Mockito.any(UUID.class), Mockito.anyString(), Mockito.anyBoolean()))
			.thenReturn(FileSystemElementTree.builder().build());
		
		Mockito.when(documentSpaceFileSystemService.flattenTreeToS3ObjectAndFilenameList(Mockito.any(FileSystemElementTree.class)))
		.thenReturn(Lists.newArrayList(
				S3ObjectAndFilename.builder()
						.s3Object(obj0)
						.pathAndFilename("/file0.txt")
						.build(),
				S3ObjectAndFilename.builder()
						.s3Object(obj1)
						.pathAndFilename("/file1.txt")
						.build()));
		
		Mockito.when(documentSpaceRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(entity));
		
		documentService.downloadAndWriteCompressedFiles(documentSpaceDto.getId(), "", Set.copyOf(fileNames), output, dashboardUser.getEmail());
		
		assertThat(output.size()).isPositive();
		Mockito.verify(metadataService).saveMetadata(Mockito.any(UUID.class), Mockito.anySet(), Mockito.any(DocumentMetadata.class), Mockito.any(DashboardUser.class));
	}

	@Test
	void testdownloadAllInDirectoryAndCompress()
			throws AmazonServiceException, AmazonClientException, InterruptedException {
		Mockito.when(documentSpaceRepo.save(Mockito.any(DocumentSpace.class))).thenReturn(entity);
		DocumentSpaceResponseDto documentSpaceDto = documentService.createSpace(requestDto);

		Mockito.when(documentSpaceFileSystemService.dumpElementTree(Mockito.any(UUID.class), Mockito.anyString(), Mockito.anyBoolean()))
				.thenReturn(FileSystemElementTree.builder()
						.value(DocumentSpaceFileSystemEntry.builder()
								.itemId(UUID.randomUUID())
								.build())
						.build());

		S3ObjectSummary obj0 = new S3ObjectSummary();
		obj0.setKey(documentService.createDocumentSpacePathPrefix(entity.getId()) + "file0.txt");
		S3ObjectSummary obj1 = new S3ObjectSummary();
		obj1.setKey(documentService.createDocumentSpacePathPrefix(entity.getId()) + "file1.txt");
		S3ObjectSummary obj2 = new S3ObjectSummary();
		obj2.setKey(documentService.createDocumentSpacePathPrefix(entity.getId()) + "file2.txt");
		S3ObjectSummary obj3 = new S3ObjectSummary();
		obj3.setKey(documentService.createDocumentSpacePathPrefix(entity.getId()) + "file3.txt");
		S3ObjectSummary obj4 = new S3ObjectSummary();
		obj4.setKey(documentService.createDocumentSpacePathPrefix(entity.getId()) + "file4.txt");

		Mockito.when(documentSpaceFileSystemService.flattenTreeToS3ObjectAndFilenameList(Mockito.any(FileSystemElementTree.class)))
				.thenReturn(Lists.newArrayList(
						S3ObjectAndFilename.builder()
							.s3Object(obj0)
							.pathAndFilename("/file0.txt")
							.build(),
						S3ObjectAndFilename.builder()
							.s3Object(obj1)
							.pathAndFilename("/file1.txt")
							.build(),
						S3ObjectAndFilename.builder()
							.s3Object(obj2)
							.pathAndFilename("/file2.txt")
							.build(),
						S3ObjectAndFilename.builder()
							.s3Object(obj3)
							.pathAndFilename("/file3.txt")
							.build(),
						S3ObjectAndFilename.builder()
							.s3Object(obj4)
							.pathAndFilename("/file4.txt")
							.build()));

		String content = "fake content";
		uploadDummyFilesUsingTransferManager(content, 5);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		documentService.downloadAllInSpaceAndCompress(documentSpaceDto.getId(), output);

		assertThat(output.size()).isPositive();
	}
	
	@Test
	void testArchiveItem() {
		Mockito.when(
				documentSpaceFileSystemService.getFilePathSpec(Mockito.any(UUID.class), Mockito.any(UUID.class)))
				.thenReturn(
						FilePathSpec.builder().itemId(DocumentSpaceFileSystemEntry.NIL_UUID).build());
		
		Mockito.doNothing().when(documentSpaceFileSystemService).archiveElement(Mockito.any(UUID.class), Mockito.anyString(), Mockito.anyString());
		
		documentService.archiveItem(UUID.randomUUID(), UUID.randomUUID(), "testfile");
		
		Mockito.verify(documentSpaceFileSystemService).archiveElement(Mockito.any(UUID.class), Mockito.anyString(), Mockito.anyString());
	}
	
	@Nested
	class GetRecentlyUploadedFilesByUserTest {
		List<RecentDocumentDto> generateRandomFileEntries(int size) {
			List<RecentDocumentDto> entries = new ArrayList<>();
			
			for (int i = 0; i < size; i++) {
				entries.add(new RecentDocumentDto(UUID.randomUUID(), RandomStringUtils.randomAlphanumeric(16), UUID.randomUUID(), new Date(), UUID.randomUUID(), "test document space"));
			}
			
			return entries;
		}
		
		private DashboardUser dashboardUser;

		@BeforeEach
		void setup() {
			dashboardUser = DashboardUser.builder()
					.id(UUID.randomUUID())
					.email("dashboard@user.com")
					.emailAsLower("dashboard@user.com")
					.privileges(new HashSet<>(Arrays.asList(new Privilege(1L, "DASHBOARD_ADMIN"))))
					.documentSpaces(new HashSet<>(Arrays.asList(entity)))
					.documentSpacePrivileges(new HashSet<>(Arrays.asList(entity.getPrivileges().get(DocumentSpacePrivilegeType.READ))))
					.build();
		}
		
		@Test
		void shouldGetFiles() {
			Mockito.when(dashboardUserService.getDashboardUserByEmailAsLower(Mockito.anyString())).thenReturn(dashboardUser);
			Mockito.when(documentSpaceRepo.findAllDynamicBy(Mockito.any())).thenReturn(Arrays.asList(responseDto));
			
			Pageable pageable = PageRequest.of(0, 100);
	    	Slice<RecentDocumentDto> serviceResponse = new SliceImpl<>(generateRandomFileEntries(1), pageable, false);
	    	
			Mockito.when(documentSpaceFileService.getRecentlyUploadedFilesByUser(Mockito.anyString(), Mockito.anySet(), Mockito.any()))
				.thenReturn(serviceResponse);

			Slice<RecentDocumentDto> dtos = documentService.getRecentlyUploadedFilesByAuthUser("test user", pageable);
			
			assertThat(dtos).hasSize(1);
		}
	}
	

	@Nested
	class AddDashboardUserToDocumentSpaceTest {
		private DashboardUser dashboardUser;
		private DocumentSpaceDashboardMemberRequestDto memberDto;

		@BeforeEach
		void setup() {
			dashboardUser = DashboardUser.builder()
					.id(UUID.randomUUID())
					.email("dashboard@user.com")
					.emailAsLower("dashboard@user.com")
					.documentSpaces(new HashSet<>(Arrays.asList(entity)))
					.documentSpacePrivileges(new HashSet<>(Arrays.asList(entity.getPrivileges().get(DocumentSpacePrivilegeType.READ))))
					.build();

			memberDto = DocumentSpaceDashboardMemberRequestDto.builder()
					.email(dashboardUser.getEmail())
					.build();
		}

		@Test
		void shouldAddDashboardUserToDocumentSpace_whenDocumentSpaceExists() {
			Mockito.when(documentSpaceRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(entity));
			Mockito.when(documentSpacePrivilegeService.createDashboardUserWithPrivileges(Mockito.anyString(),
					Mockito.any(DocumentSpace.class), Mockito.anyList())).thenReturn(dashboardUser);

			documentService.addDashboardUserToDocumentSpace(entity.getId(), memberDto);

			assertThat(entity.getDashboardUsers()).contains(dashboardUser);
			assertThat(dashboardUser.getDocumentSpaces()).contains(entity);
			assertThat(dashboardUser.getDocumentSpacePrivileges()).contains(entity.getPrivileges().get(DocumentSpacePrivilegeType.READ));
		}

		@Test
		void shouldThrow_whenDocumentSpaceNotExists() {
			Mockito.when(documentSpaceRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.ofNullable(null));
			UUID invalidId = UUID.randomUUID();
			assertThatThrownBy(() -> documentService.addDashboardUserToDocumentSpace(invalidId, memberDto))
				.isInstanceOf(RecordNotFoundException.class)
				.hasMessageContaining(String.format("Document Space with id: %s not found", invalidId));
		}
	}

	@Nested
	class RemoveDashboardUserFromDocumentSpaceTest {
		private DashboardUser dashboardUser;
		private DocumentSpaceDashboardMemberRequestDto memberDto;
		private Privilege documentSpacePrivilege;
		private Privilege dashboardUserPrivilege;

		@BeforeEach
		void setup() {
			documentSpacePrivilege = new Privilege(44L, DocumentSpaceServiceImpl.DOCUMENT_SPACE_USER_PRIVILEGE);
			dashboardUserPrivilege = new Privilege(1L, "DASHBOARD_USER");

			dashboardUser = DashboardUser.builder()
					.id(UUID.randomUUID())
					.email("dashboard@user.com")
					.emailAsLower("dashboard@user.com")
					.privileges(new HashSet<>(Arrays.asList(documentSpacePrivilege, dashboardUserPrivilege)))
					.documentSpaces(new HashSet<>(Arrays.asList(entity)))
					.documentSpacePrivileges(new HashSet<>(Arrays.asList(entity.getPrivileges().get(DocumentSpacePrivilegeType.READ))))
					.build();

			memberDto = DocumentSpaceDashboardMemberRequestDto.builder()
					.email(dashboardUser.getEmail())
					.build();
		}

		@Test
		void shouldRemoveDashboardUserFromDocumentSpace_whenDocumentSpaceExists() {
			Mockito.when(documentSpaceRepo.findById(entity.getId())).thenReturn(Optional.of(entity));
			Mockito.doReturn(dashboardUser).when(dashboardUserService).getDashboardUserByEmailAsLower(dashboardUser.getEmail());

			documentService.removeDashboardUserFromDocumentSpace(entity.getId(), new String[] {memberDto.getEmail()});

			assertThat(entity.getDashboardUsers()).doesNotContain(dashboardUser);
			assertThat(dashboardUser.getDocumentSpaces()).doesNotContain(entity);
			Mockito.verify(documentSpacePrivilegeService).removePrivilegesFromDashboardUser(dashboardUser,entity);
		}

		@Test
		void shouldDeleteDashboardUser_whenUserHasNoMembershipAndNoOtherPrivilege() {
			Mockito.when(documentSpaceRepo.findById(entity.getId())).thenReturn(Optional.of(entity));
			Mockito.doReturn(dashboardUser).when(dashboardUserService).getDashboardUserByEmailAsLower(dashboardUser.getEmail());
			Mockito.when(privilegeRepository.findByName(Mockito.anyString())).thenReturn(Optional.of(documentSpacePrivilege));

			documentService.removeDashboardUserFromDocumentSpace(entity.getId(), new String[] {memberDto.getEmail()});

			assertThat(entity.getDashboardUsers()).doesNotContain(dashboardUser);
			assertThat(dashboardUser.getDocumentSpaces()).doesNotContain(entity);
			Mockito.verify(documentSpacePrivilegeService).removePrivilegesFromDashboardUser(dashboardUser,entity);
			Mockito.verify(dashboardUserService).deleteDashboardUser(dashboardUser.getId());
		}

		@Test
		void shouldNotDeleteDashboardUser_whenUserHasOtherPrivileges() {
			Privilege dashboardAdmin = new Privilege(43L, "DASHBOARD_ADMIN");
			dashboardUser.getPrivileges().add(dashboardAdmin);

			Mockito.when(documentSpaceRepo.findById(entity.getId())).thenReturn(Optional.of(entity));
			Mockito.doReturn(dashboardUser).when(dashboardUserService).getDashboardUserByEmailAsLower(dashboardUser.getEmail());
			Mockito.when(privilegeRepository.findByName(Mockito.anyString())).thenReturn(Optional.of(documentSpacePrivilege));

			documentService.removeDashboardUserFromDocumentSpace(entity.getId(), new String[] {memberDto.getEmail()});

			assertThat(entity.getDashboardUsers()).doesNotContain(dashboardUser);
			assertThat(dashboardUser.getDocumentSpaces()).doesNotContain(entity);
			Mockito.verify(documentSpacePrivilegeService).removePrivilegesFromDashboardUser(dashboardUser,entity);
			Mockito.verify(dashboardUserService, Mockito.never()).deleteDashboardUser(dashboardUser.getId());
		}

		@Test
		void shouldThrow_whenDocumentSpaceNotExists() {
			UUID invalidId = UUID.randomUUID();

			Mockito.when(documentSpaceRepo.findById(invalidId)).thenReturn(Optional.ofNullable(null));
			assertThatThrownBy(() -> documentService.removeDashboardUserFromDocumentSpace(invalidId, new String[] {memberDto.getEmail()}))
				.isInstanceOf(RecordNotFoundException.class)
				.hasMessageContaining(String.format("Document Space with id: %s not found", invalidId));
		}
	}

	@Nested
	class GetDashboardUsersForDocumentSpaceTest {
		private DashboardUser dashboardUserA;
		private DashboardUser dashboardUserB;

		private DocumentSpacePrivilegeDto readDto;
		private DocumentSpacePrivilegeDto writeDto;

		private DocumentSpaceDashboardMemberPrivilegeRow privilegeRowA;
		private DocumentSpaceDashboardMemberPrivilegeRow privilegeRowB;

		private DocumentSpaceDashboardMemberResponseDto responseDtoA;
		private DocumentSpaceDashboardMemberResponseDto responseDtoB;

		@BeforeEach
		void setup() {
			DocumentSpacePrivilege read = entity.getPrivileges().get(DocumentSpacePrivilegeType.READ);
			DocumentSpacePrivilege write = entity.getPrivileges().get(DocumentSpacePrivilegeType.WRITE);

			dashboardUserA = DashboardUser.builder()
					.id(UUID.randomUUID())
					.email("dashboardA@user.com")
					.emailAsLower("dashboarda@user.com")
					.documentSpaces(new HashSet<>(Arrays.asList(entity)))
					.documentSpacePrivileges(new HashSet<>(Arrays.asList(read)))
					.build();

			dashboardUserB = DashboardUser.builder()
					.id(UUID.randomUUID())
					.email("dashboardB@user.com")
					.emailAsLower("dashboardb@user.com")
					.documentSpaces(new HashSet<>(Arrays.asList(entity)))
					.documentSpacePrivileges(new HashSet<>(Arrays.asList(write)))
					.build();

			readDto = new DocumentSpacePrivilegeDto(read.getId(), read.getType());
			writeDto = new DocumentSpacePrivilegeDto(write.getId(), write.getType());

			privilegeRowA = new DocumentSpaceDashboardMemberPrivilegeRow(dashboardUserA.getId(), dashboardUserA.getEmail(), read);
			privilegeRowB = new DocumentSpaceDashboardMemberPrivilegeRow(dashboardUserB.getId(), dashboardUserB.getEmail(), write);

			responseDtoA = new DocumentSpaceDashboardMemberResponseDto(dashboardUserA.getId(), dashboardUserA.getEmail(), Arrays.asList(readDto));
			responseDtoB = new DocumentSpaceDashboardMemberResponseDto(dashboardUserB.getId(), dashboardUserB.getEmail(), Arrays.asList(writeDto));
		}

		@Test
		void shouldReturnSorted_whenPageableIncludesSort() {
			Mockito.when(documentSpaceRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(entity));

			PageRequest pageable = PageRequest.of(0, 10, Sort.by(new Order(Direction.DESC, "email")));

			Mockito.when(dashboardUserRepository.findAllByDocumentSpaces_Id(Mockito.any(UUID.class), Mockito.any(Pageable.class)))
				.thenReturn(new PageImpl<>(Arrays.asList(dashboardUserB, dashboardUserA), pageable, 2));
			Mockito.when(documentSpacePrivilegeService.getAllDashboardMemberPrivilegeRowsForDocumentSpace(Mockito.any(DocumentSpace.class), Mockito.anySet()))
				.thenReturn(Arrays.asList(privilegeRowA, privilegeRowB));

			Page<DocumentSpaceDashboardMemberResponseDto> result = documentService.getDashboardUsersForDocumentSpace(entity.getId(), pageable);

			assertThat(result.getContent()).containsExactlyElementsOf(Arrays.asList(responseDtoB, responseDtoA));
		}

		@Test
		void shouldReturnUnsorted_whenPageableNotIncludesSort() {
			Mockito.when(documentSpaceRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(entity));

			PageRequest pageable = PageRequest.of(0, 10);

			Mockito.when(dashboardUserRepository.findAllByDocumentSpaces_Id(Mockito.any(UUID.class), Mockito.any(Pageable.class)))
				.thenReturn(new PageImpl<>(Arrays.asList(dashboardUserB, dashboardUserA), pageable, 2));
			Mockito.when(documentSpacePrivilegeService.getAllDashboardMemberPrivilegeRowsForDocumentSpace(Mockito.any(DocumentSpace.class), Mockito.anySet()))
				.thenReturn(Arrays.asList(privilegeRowA, privilegeRowB));

			Page<DocumentSpaceDashboardMemberResponseDto> result = documentService.getDashboardUsersForDocumentSpace(entity.getId(), pageable);

			assertThat(result.getContent()).containsExactlyInAnyOrderElementsOf(Arrays.asList(responseDtoB, responseDtoA));
		}

		@Test
		void shouldThrow_whenDocumentSpaceNotExists() {
			Mockito.when(documentSpaceRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.ofNullable(null));
			UUID invalidId = UUID.randomUUID();
			assertThatThrownBy(() -> documentService.getDashboardUsersForDocumentSpace(invalidId, null))
				.isInstanceOf(RecordNotFoundException.class)
				.hasMessageContaining(String.format("Document Space with id: %s not found", invalidId));
		}
	}

	@Nested
	class GetDashboardMemberPrivilegesForDocumentSpaceTest {
		private DashboardUser dashboardUser;
		private DocumentSpaceDashboardMemberPrivilegeRow privilegeRow;

		@BeforeEach
		void setup() {
			dashboardUser = DashboardUser.builder()
					.id(UUID.randomUUID())
					.email("dashboard@user.com")
					.emailAsLower("dashboard@user.com")
					.documentSpaces(new HashSet<>(Arrays.asList(entity)))
					.documentSpacePrivileges(new HashSet<>(Arrays.asList(entity.getPrivileges().get(DocumentSpacePrivilegeType.READ))))
					.build();

			DocumentSpacePrivilege read = entity.getPrivileges().get(DocumentSpacePrivilegeType.READ);
			privilegeRow = new DocumentSpaceDashboardMemberPrivilegeRow(dashboardUser.getId(), dashboardUser.getEmail(), read);

		}

		@Test
		void shouldGetPrivileges_whenMemberOfDocumentSpace() {
			Mockito.when(documentSpaceRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(entity));
			Mockito.when(dashboardUserService.getDashboardUserByEmailAsLower(Mockito.anyString())).thenReturn(dashboardUser);
			Mockito.when(documentSpacePrivilegeService.getAllDashboardMemberPrivilegeRowsForDocumentSpace(
					Mockito.any(DocumentSpace.class), Mockito.anySet())).thenReturn(List.of(privilegeRow));

			List<DocumentSpacePrivilegeDto> privileges = documentService.getDashboardUserPrivilegesForDocumentSpace(entity.getId(), dashboardUser.getEmail());
			assertThat(privileges)
				.isNotEmpty()
				.hasSize(1)
				.contains(new DocumentSpacePrivilegeDto(privilegeRow.getPrivilege().getId(), privilegeRow.getPrivilege().getType()));
		}

		@Test
		void shouldThrow_whenDashboardUserHasNoPrivilegesToDocumentSpace() {
			Mockito.when(documentSpaceRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(entity));
			Mockito.when(dashboardUserService.getDashboardUserByEmailAsLower(Mockito.anyString())).thenReturn(dashboardUser);
			Mockito.when(documentSpacePrivilegeService.getAllDashboardMemberPrivilegeRowsForDocumentSpace(
					Mockito.any(DocumentSpace.class), Mockito.anySet())).thenReturn(List.of());

			assertThatThrownBy(() -> documentService.getDashboardUserPrivilegesForDocumentSpace(entity.getId(), dashboardUser.getEmail()))
				.isInstanceOf(NotAuthorizedException.class)
				.hasMessageContaining("Not Authorized to this Document Space");
		}

		@Test
		void shouldThrow_whenDocumentSpaceNotExists() {
			Mockito.when(documentSpaceRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.ofNullable(null));

			assertThatThrownBy(() -> documentService.getDashboardUserPrivilegesForDocumentSpace(entity.getId(), dashboardUser.getEmail()))
				.isInstanceOf(RecordNotFoundException.class)
				.hasMessageContaining(String.format("Document Space with id: %s not found", entity.getId()));
		}

		@Test
		void shouldThrow_whenDashboardUserNotExists() {
			Mockito.when(documentSpaceRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(entity));
			Mockito.when(dashboardUserService.getDashboardUserByEmailAsLower(Mockito.anyString())).thenReturn(null);

			assertThatThrownBy(() -> documentService.getDashboardUserPrivilegesForDocumentSpace(entity.getId(), dashboardUser.getEmail()))
				.isInstanceOf(RecordNotFoundException.class)
				.hasMessageContaining(String.format("Requesting Document Space Dashboard User does not exist with email: ", dashboardUser.getEmail()));
		}
	}

	@Nested
	class BatchAddDashboardUserToDocumentSpaceTest {
		private DashboardUser dashboardUser;

		private UUID documentSpaceId;

		@BeforeEach
		void setup() {

			documentSpaceId = UUID.randomUUID();
			dashboardUser = DashboardUser.builder()
					.id(UUID.randomUUID())
					.email("dashboard@user.com")
					.emailAsLower("dashboard@user.com")
					.documentSpaces(new HashSet<>(Arrays.asList(entity)))
					.documentSpacePrivileges(new HashSet<>(Arrays.asList(entity.getPrivileges().get(DocumentSpacePrivilegeType.READ))))
					.build();

		}

		@Test
		void shouldAddDashboardUsersToDocumentSpace() throws IOException {
			Mockito.when(documentSpaceRepo.findById(documentSpaceId)).thenReturn(Optional.of(entity));
			Mockito.when(documentSpacePrivilegeService.createDashboardUserWithPrivileges(Mockito.anyString(),
					Mockito.any(DocumentSpace.class), Mockito.anyList())).thenReturn(dashboardUser);

			MockMultipartFile file = new MockMultipartFile("filename.txt", new FileInputStream("src/test/resources/dashboard-user-csv/happy-case.csv"));
			List<String> exceptionStrings = documentService.batchAddDashboardUserToDocumentSpace(documentSpaceId, file);
			assertEquals(0, exceptionStrings.size());
			Mockito.verify(documentSpaceRepo, times(1)).save(Mockito.any());
		}

		@Test
		void shouldThrow_whenInvalidEmailHeadersExist() throws IOException {
			Mockito.when(documentSpaceRepo.findById(documentSpaceId)).thenReturn(Optional.of(entity));

			MockMultipartFile file = new MockMultipartFile("filename.txt", new FileInputStream("src/test/resources/dashboard-user-csv/invalid-email-header.csv"));

			List<String> exceptionStrings = documentService.batchAddDashboardUserToDocumentSpace(documentSpaceId, file);
			assertEquals(1, exceptionStrings.size());

			assertEquals("Improper first CSV header: should be 'email'", exceptionStrings.get(0));
		}

		@Test
		void shouldThrow_whenInvalidPrivilegeHeadersExist() throws IOException {
			Mockito.when(documentSpaceRepo.findById(documentSpaceId)).thenReturn(Optional.of(entity));

			MockMultipartFile file = new MockMultipartFile("filename.txt", new FileInputStream("src/test/resources/dashboard-user-csv/invalid-privilege-header.csv"));

			List<String> exceptionStrings = documentService.batchAddDashboardUserToDocumentSpace(documentSpaceId, file);
			assertEquals(1, exceptionStrings.size());

			assertEquals("Improper second CSV header: should be 'privilege'", exceptionStrings.get(0));
		}

		@Test
		void shouldThrow_whenInvalidUserEmail() throws IOException {
			Mockito.when(documentSpaceRepo.findById(documentSpaceId)).thenReturn(Optional.of(entity));

			MockMultipartFile file = new MockMultipartFile("filename.txt", new FileInputStream("src/test/resources/dashboard-user-csv/invalid-empty-user-email.csv"));

			List<String> exceptionStrings = documentService.batchAddDashboardUserToDocumentSpace(documentSpaceId, file);
			assertEquals(1, exceptionStrings.size());

			assertEquals("Missing email on row 2", exceptionStrings.get(0));
		}

		@Test
		void shouldThrow_whenMissingPermission() throws IOException {
			Mockito.when(documentSpaceRepo.findById(documentSpaceId)).thenReturn(Optional.of(entity));

			MockMultipartFile file = new MockMultipartFile("filename.txt", new FileInputStream("src/test/resources/dashboard-user-csv/invalid-permission.csv"));

			List<String> exceptionStrings = documentService.batchAddDashboardUserToDocumentSpace(documentSpaceId, file);
			assertEquals(3, exceptionStrings.size());

			assertEquals("Improper minimum row length on row 2", exceptionStrings.get(0));
			assertEquals("Invalid permission on row 3 - granted 'VIEWER'", exceptionStrings.get(1));
			assertEquals("Improper minimum row length on row 4", exceptionStrings.get(2));
		}

		@Test
		void shouldThrow_whenUserAlreadyExists() throws IOException {
			entity.addDashboardUser(DashboardUser.builder().email("1@tron.dev").build());
			Mockito.when(documentSpaceRepo.findById(documentSpaceId)).thenReturn(Optional.of(entity));

			MockMultipartFile file = new MockMultipartFile("filename.txt", new FileInputStream("src/test/resources/dashboard-user-csv/happy-case.csv"));

			List<String> exceptionStrings = documentService.batchAddDashboardUserToDocumentSpace(documentSpaceId, file);
			assertEquals(1, exceptionStrings.size());

			assertEquals("Unable to add user with email 1@tron.dev, they are already a part of the space", exceptionStrings.get(0));
		}

		@Test
		void shouldThrow_whenDuplicateEmailIsFound() throws IOException {
			Mockito.when(documentSpaceRepo.findById(documentSpaceId)).thenReturn(Optional.of(entity));

			MockMultipartFile file = new MockMultipartFile("filename.txt", new FileInputStream("src/test/resources/dashboard-user-csv/duplicate-email-case.csv"));

			List<String> exceptionStrings = documentService.batchAddDashboardUserToDocumentSpace(documentSpaceId, file);
			assertEquals(1, exceptionStrings.size());

			assertEquals("Duplicate email found on row 4", exceptionStrings.get(0));
		}

	}

	@Nested
	class SetDashboardUserDefaultDocumentSpace {
		private DashboardUser dashboardUser;

		private UUID documentSpaceId;

		@BeforeEach
		void setup() {

			documentSpaceId = UUID.randomUUID();
			dashboardUser = DashboardUser.builder()
					.id(UUID.randomUUID())
					.email("dashboard@user.com")
					.emailAsLower("dashboard@user.com")
					.documentSpaces(new HashSet<>(Arrays.asList(entity)))
					.documentSpacePrivileges(new HashSet<>(Arrays.asList(entity.getPrivileges().get(DocumentSpacePrivilegeType.READ))))
					.build();

		}

		@Test
		void shouldSetDashboardUserDefaultDocumentSpaceAndSave() {
			Mockito.when(documentSpaceRepo.findById(documentSpaceId)).thenReturn(Optional.of(entity));
			Mockito.doReturn(dashboardUser).when(dashboardUserService).getDashboardUserByEmailAsLower(dashboardUser.getEmail());


			entity.addDashboardUser(dashboardUser);
			Assert.assertNull(dashboardUser.getDefaultDocumentSpaceId());

			documentService.setDashboardUserDefaultDocumentSpace(documentSpaceId, dashboardUser.getEmail());

			dashboardUser.setDefaultDocumentSpaceId(documentSpaceId);
			Mockito.verify(dashboardUserRepository, times(1)).save(dashboardUser);
		}

		@Test
		void shouldThrow_whenDashboardUserIsNotFound() {
			Mockito.when(documentSpaceRepo.findById(documentSpaceId)).thenReturn(Optional.of(entity));

			assertThatThrownBy(() -> documentService.setDashboardUserDefaultDocumentSpace(documentSpaceId, dashboardUser.getEmail()))
					.isInstanceOf(RecordNotFoundException.class)
					.hasMessageContaining(String.format("Requesting Document Space Dashboard User does not exist with email: ", dashboardUser.getEmail()));
		}

	}

	@Nested
	class UnsetDashboardUsersDefaultDocumentSpace {
		private DashboardUser dashboardUser;

		@BeforeEach
		void setup() {
			dashboardUser = DashboardUser.builder()
					.id(UUID.randomUUID())
					.email("dashboard@user.com")
					.emailAsLower("dashboard@user.com")
					.documentSpaces(new HashSet<>(Arrays.asList(entity)))
					.defaultDocumentSpaceId(entity.getId())
					.documentSpacePrivileges(new HashSet<>(Arrays.asList(entity.getPrivileges().get(DocumentSpacePrivilegeType.READ))))
					.build();

			entity.addDashboardUser(dashboardUser);

		}

		@Test
		void shouldRemoveAllDefaultDocumentSpaceIdsFromDashboardUsers() {
			assertNotNull(dashboardUser.getDefaultDocumentSpaceId());
			documentService.unsetDashboardUsersDefaultDocumentSpace(entity);
			Mockito.verify(dashboardUserRepository, times(1)).unsetDashboardUsersDefaultDocumentSpaceForDocumentSpace(entity.getId());
		}
	}

}
