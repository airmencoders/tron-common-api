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
import mil.tron.commonapi.dto.documentspace.DocumentSpaceDashboardMemberRequestDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceDashboardMemberResponseDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpacePrivilegeDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceResponseDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceRequestDto;
import mil.tron.commonapi.dto.documentspace.S3PaginationDto;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.documentspace.DocumentSpace;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceDashboardMemberPrivilegeRow;
import mil.tron.commonapi.entity.documentspace.DocumentSpacePrivilege;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
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
	
	@Mock
	private DashboardUserRepository dashboardUserRepository;
	
	@Mock
	private DocumentSpacePrivilegeService documentSpacePrivilegeService;

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
				.withPathStyleAccessEnabled(true).withClientConfiguration(clientConfiguration)
				.withCredentials(new AWSStaticCredentialsProvider(awsCredentials)).build();

		transferManager = TransferManagerBuilder.standard().withS3Client(amazonS3).build();

		documentService = new DocumentSpaceServiceImpl(amazonS3, transferManager, BUCKET_NAME, documentSpaceRepo,
				documentSpacePrivilegeService, dashboardUserRepository);
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
		MockMultipartFile file = new MockMultipartFile("filename.txt", "filename.txt", "multipart/form-data",
				fakeContent.getBytes());

		Mockito.when(documentSpaceRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(entity));
		documentService.uploadFile(entity.getId(), file);

		assertThat(amazonS3.doesObjectExist(BUCKET_NAME,
				documentService.createDocumentSpacePathPrefix(entity.getId()) + "filename.txt")).isTrue();
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
		documentService.deleteFile(entity.getId(), fileNames.get(0));

		assertThat(amazonS3.doesObjectExist(BUCKET_NAME,
				documentService.createDocumentSpacePathPrefix(entity.getId()) + fileNames.get(0))).isFalse();
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
				.ignoringFields("uploadedDate", "uploadedBy")
				.isEqualTo(fileNames.stream().map(filename -> DocumentDto.builder().key(filename).path(requestDto.getId() + "/")
						.size(content.getBytes().length).build()).collect(Collectors.toList()));
	}

	@Test
	void testDownloadFile() throws AmazonServiceException, AmazonClientException, InterruptedException {
		Mockito.when(documentSpaceRepo.save(Mockito.any(DocumentSpace.class))).thenReturn(entity);
		DocumentSpaceResponseDto documentSpaceDto = documentService.createSpace(requestDto);

		String content = "fake content";
		List<String> fileNames = uploadDummyFilesUsingTransferManager(content, 20);

		Mockito.when(documentSpaceRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(entity));
		S3Object downloadFile = documentService.downloadFile(documentSpaceDto.getId(), fileNames.get(0));

		S3Object actual = amazonS3.getObject(BUCKET_NAME,
				documentService.createDocumentSpacePathPrefix(entity.getId()) + fileNames.get(0));
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
			fromS3.add(amazonS3.getObject(BUCKET_NAME,
					documentService.createDocumentSpacePathPrefix(entity.getId()) + fileNames.get(i)));
		}

		assertThat(downloadFiles).hasSameSizeAs(fromS3);
		for (int i = 0; i < downloadFiles.size(); i++) {
			String key = downloadFiles.get(i).getKey();

			assertThat(fromS3).anyMatch(actual -> actual.getKey().equals(key));
		}
	}

	@Test
	void testDownloadAndWriteCompressedFiles()
			throws AmazonServiceException, AmazonClientException, InterruptedException {
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
	void testdownloadAllInDirectoryAndCompress()
			throws AmazonServiceException, AmazonClientException, InterruptedException {
		Mockito.when(documentSpaceRepo.save(Mockito.any(DocumentSpace.class))).thenReturn(entity);
		DocumentSpaceResponseDto documentSpaceDto = documentService.createSpace(requestDto);

		String content = "fake content";
		uploadDummyFilesUsingTransferManager(content, 5);

		ByteArrayOutputStream output = new ByteArrayOutputStream();

		Mockito.when(documentSpaceRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(entity));
		documentService.downloadAllInSpaceAndCompress(documentSpaceDto.getId(), output);

		assertThat(output.size()).isPositive();
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
					.privileges(Arrays.asList(DocumentSpacePrivilegeType.READ))
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
}
