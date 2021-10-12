package mil.tron.commonapi.service.documentspace;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.s3.model.DeleteObjectsRequest.KeyVersion;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceRequestDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceDashboardMemberRequestDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceDashboardMemberResponseDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpacePrivilegeDto;
import mil.tron.commonapi.dto.documentspace.S3PaginationDto;
import lombok.extern.slf4j.Slf4j;
import mil.tron.commonapi.annotation.minio.IfMinioEnabledOnStagingIL4OrDevLocal;
import mil.tron.commonapi.dto.documentspace.*;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.documentspace.DocumentSpace;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceDashboardMember;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceDashboardMemberPrivilegeRow;
import mil.tron.commonapi.entity.documentspace.DocumentSpacePrivilege;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceRepository;
import mil.tron.commonapi.service.DashboardUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@IfMinioEnabledOnStagingIL4OrDevLocal
public class DocumentSpaceServiceImpl implements DocumentSpaceService {
	private final AmazonS3 documentSpaceClient;
	private final TransferManager documentSpaceTransferManager;
	private final String bucketName;

	private final DocumentSpaceRepository documentSpaceRepository;
	private final DashboardUserRepository dashboardUserRepository;
	private final DocumentSpaceFileSystemService documentSpaceFileSystemService;

	private final DocumentSpacePrivilegeService documentSpacePrivilegeService;

	@Value("${spring.profiles.active:UNKNOWN}")
	private String activeProfile;

	@Value("${ENCLAVE_LEVEL:UNKNOWN}")
	private String enclaveLevel;

	private final DashboardUserService dashboardUserService;
	public DocumentSpaceServiceImpl(AmazonS3 documentSpaceClient, TransferManager documentSpaceTransferManager,
									@Value("${minio.bucket-name}") String bucketName, DocumentSpaceRepository documentSpaceRepository,
									DocumentSpacePrivilegeService documentSpacePrivilegeService, DashboardUserRepository dashboardUserRepository,
									DashboardUserService dashboardUserService, DocumentSpaceFileSystemService documentSpaceFileSystemService) {

		this.documentSpaceClient = documentSpaceClient;
		this.documentSpaceTransferManager = documentSpaceTransferManager;
		this.bucketName = bucketName;

		this.documentSpaceRepository = documentSpaceRepository;
		this.dashboardUserRepository = dashboardUserRepository;

		this.documentSpaceFileSystemService = documentSpaceFileSystemService;

		this.documentSpacePrivilegeService = documentSpacePrivilegeService;
		this.dashboardUserService = dashboardUserService;
	}

	/**
	 * Until we get a real minio bucket from P1... only run
	 */
	@PostConstruct
	public void setupBucket() {
		try {
			if (!this.documentSpaceClient.doesBucketExistV2(this.bucketName))
				this.documentSpaceClient.createBucket(this.bucketName);
		}
		catch (SdkClientException ex) {
			Logger.getLogger("DocumentServiceLogger").warning(ex.getMessage());
		}
	}

	@Override
	public List<DocumentSpaceResponseDto> listSpaces() {
		return documentSpaceRepository.findAllDynamicBy(DocumentSpaceResponseDto.class);
	}

	@Override
	public DocumentSpaceResponseDto createSpace(DocumentSpaceRequestDto dto) {
		DocumentSpace documentSpace = convertDocumentSpaceRequestDtoToEntity(dto);

		if (documentSpaceRepository.existsByName(documentSpace.getName())) {
			throw new ResourceAlreadyExistsException(
					String.format("Document Space with the name: %s already exists", documentSpace.getName()));
		}

		documentSpace = documentSpaceRepository.save(documentSpace);
		documentSpacePrivilegeService.createAndSavePrivilegesForNewSpace(documentSpace);

		return convertDocumentSpaceEntityToResponseDto(documentSpaceRepository.save(documentSpace));
	}

	@Override
	public void deleteSpace(UUID documentSpaceId) throws RecordNotFoundException {
		DocumentSpace documentSpace = getDocumentSpaceOrElseThrow(documentSpaceId);

		ListObjectsRequest objectRequest = new ListObjectsRequest().withBucketName(bucketName)
				.withPrefix(createDocumentSpacePathPrefix(documentSpace.getId()));

		ObjectListing objectListing = null;

		do {
			if (objectListing == null) {
				objectListing = documentSpaceClient.listObjects(objectRequest);
			} else {
				objectListing = documentSpaceClient.listNextBatchOfObjects(objectListing);
			}

			if (!objectListing.getObjectSummaries().isEmpty()) {
				List<KeyVersion> keys = objectListing.getObjectSummaries().stream()
						.map(objectSummary -> new KeyVersion(objectSummary.getKey())).collect(Collectors.toList());

				DeleteObjectsRequest multiObjectDeleteRequest = new DeleteObjectsRequest(bucketName).withKeys(keys);

				documentSpaceClient.deleteObjects(multiObjectDeleteRequest);
			}

		} while (objectListing.isTruncated());

		documentSpacePrivilegeService.deleteAllPrivilegesBelongingToDocumentSpace(documentSpace);
		documentSpaceRepository.deleteById(documentSpace.getId());
	}

	@Override
	public S3Object getFile(UUID documentSpaceId, String path, String key) throws RecordNotFoundException {
		DocumentSpace documentSpace = getDocumentSpaceOrElseThrow(documentSpaceId);
		FilePathSpec spec = documentSpaceFileSystemService.parsePathToFilePathSpec(documentSpaceId, path);
		String prefix = !path.isBlank() && !spec.getDocSpaceQualifiedPath().isBlank()
				? spec.getDocSpaceQualifiedPath()
				: this.createDocumentSpacePathPrefix(documentSpace.getId());
		return documentSpaceClient.getObject(bucketName, prefix + key);
	}

	@Override
	public S3Object downloadFile(UUID documentSpaceId, String path, String fileKey) throws RecordNotFoundException {
		return getFile(documentSpaceId, path, fileKey);
	}

	@Override
	public List<S3Object> getFiles(UUID documentSpaceId, String path, Set<String> fileKeys) throws RecordNotFoundException {
		DocumentSpace documentSpace = getDocumentSpaceOrElseThrow(documentSpaceId);
		FilePathSpec spec = documentSpaceFileSystemService.parsePathToFilePathSpec(documentSpaceId, path);
		String prefix = !path.isBlank() && !spec.getDocSpaceQualifiedPath().isBlank()
				? spec.getDocSpaceQualifiedPath()
				: this.createDocumentSpacePathPrefix(documentSpace.getId());

		return fileKeys.stream().map(
				item -> documentSpaceClient.getObject(bucketName, prefix + item))
				.collect(Collectors.toList());
	}

	@Override
	public void downloadAndWriteCompressedFiles(UUID documentSpaceId, String path, Set<String> fileKeys, OutputStream out)
			throws RecordNotFoundException {
		List<S3Object> files = getFiles(documentSpaceId, path, fileKeys);

		try (BufferedOutputStream bos = new BufferedOutputStream(out); ZipOutputStream zipOut = new ZipOutputStream(bos);) {
			files.forEach(item -> {
				ZipEntry entry = new ZipEntry(item.getKey());

				try (S3ObjectInputStream dataStream = item.getObjectContent()) {
					zipOut.putNextEntry(entry);

					dataStream.transferTo(zipOut);

					zipOut.closeEntry();
				} catch (IOException e) {
					log.warn("Failed to compress file: " + item.getKey());
				}
			});

			zipOut.finish();
		} catch (IOException e1) {
			log.warn("Failure occurred closing zip output stream");
		}
	}

	@Override
	public void uploadFile(UUID documentSpaceId, String path, MultipartFile file) throws RecordNotFoundException {
		DocumentSpace documentSpace = getDocumentSpaceOrElseThrow(documentSpaceId);
		FilePathSpec spec = documentSpaceFileSystemService.parsePathToFilePathSpec(documentSpaceId, path);
		ObjectMetadata metaData = new ObjectMetadata();
		metaData.setContentType(file.getContentType());
		metaData.setContentLength(file.getSize());

		String prefix = !path.isBlank() && !spec.getDocSpaceQualifiedPath().isBlank()
				? spec.getDocSpaceQualifiedPath()
				: this.createDocumentSpacePathPrefix(documentSpace.getId());

		try {
			Upload upload = documentSpaceTransferManager.upload(bucketName,
					prefix + file.getOriginalFilename(), file.getInputStream(),
					metaData);

			upload.waitForCompletion();
		} catch (IOException | InterruptedException e) { // NOSONAR
			throw new BadRequestException("Failed retrieving input stream");
		}
	}

	@Override
	public void deleteFile(UUID documentSpaceId, String path, String file) throws RecordNotFoundException {
		DocumentSpace documentSpace = getDocumentSpaceOrElseThrow(documentSpaceId);
		FilePathSpec spec = documentSpaceFileSystemService.parsePathToFilePathSpec(documentSpaceId, path);
		String prefix = !path.isBlank() && !spec.getDocSpaceQualifiedPath().isBlank()
				? spec.getDocSpaceQualifiedPath()
				: this.createDocumentSpacePathPrefix(documentSpace.getId());

		String fileKey = prefix + file;
		try {
			documentSpaceClient.deleteObject(bucketName, fileKey);
		} catch (AmazonServiceException ex) {
			if (ex.getStatusCode() == 404) {
				throw new RecordNotFoundException(String.format("File to delete: %s does not exist", fileKey));
			}

			throw ex;
		}

	}

	@Override
	public FilePathSpec createFolder(UUID documentSpaceId, String path, String name) {
		return documentSpaceFileSystemService.convertFileSystemEntityToFilePathSpec(
				documentSpaceFileSystemService.addFolder(documentSpaceId, name, path));
	}

	@Override
	public void deleteFolder(UUID documentSpaceId, String path) {
		documentSpaceFileSystemService.deleteFolder(documentSpaceId, path);
	}

	@Override
	public FilePathSpec getFolderContents(UUID documentSpaceId, String path) {
		return documentSpaceFileSystemService.parsePathToFilePathSpec(documentSpaceId, path);
	}

	@Override
	public S3PaginationDto listFiles(UUID documentSpaceId, String path, String continuationToken, @Nullable Integer limit)
			throws RecordNotFoundException {
		DocumentSpace documentSpace = getDocumentSpaceOrElseThrow(documentSpaceId);
		FilePathSpec spec = documentSpaceFileSystemService.parsePathToFilePathSpec(documentSpaceId, path);
		String prefix = !path.isBlank() && !spec.getDocSpaceQualifiedPath().isBlank()
				? spec.getDocSpaceQualifiedPath()
				: this.createDocumentSpacePathPrefix(documentSpace.getId());

		if (limit == null) {
			limit = 20;
		}

		ListObjectsV2Request request = new ListObjectsV2Request().withBucketName(bucketName)
				.withPrefix(prefix).withMaxKeys(limit)
				.withContinuationToken(continuationToken);

		ListObjectsV2Result objectListing = documentSpaceClient.listObjectsV2(request);
		List<S3ObjectSummary> summary = objectListing.getObjectSummaries();

		List<DocumentDto> documents = summary.stream()
				.map(item -> this.convertS3SummaryToDto(prefix, item))
				.collect(Collectors.toList());

		return S3PaginationDto.builder().currentContinuationToken(objectListing.getContinuationToken())
				.nextContinuationToken(objectListing.getNextContinuationToken()).documents(documents).size(limit)
				.totalElements(documents.size()).build();
	}

	@Override
	public void downloadAllInSpaceAndCompress(UUID documentSpaceId, OutputStream out) throws RecordNotFoundException {
		DocumentSpace documentSpace = getDocumentSpaceOrElseThrow(documentSpaceId);

		try (BufferedOutputStream bos = new BufferedOutputStream(out);
			 ZipOutputStream zipOut = new ZipOutputStream(bos)) {

			this.getAllFilesInFolder(documentSpace.getId(), this.createDocumentSpacePathPrefix(documentSpace.getId()))
					.forEach(s3Object -> insertS3ObjectZipEntry(zipOut, s3Object));
			zipOut.finish();
		} catch (IOException e1) {
			log.warn("Failure occurred closing zip output stream");
		}
	}

	/**
	 * Gets all S3Objects (files) in a given "folder" (prefix) one-level deep
	 * Prefix is the "path" leading up to and including the path from which to get a list of files
	 * e.g. (/`doc-space-uuid`/`some-folder-uuid`/) would be a prefix
	 * To get a list at the root level, prefix would just be (`/doc-space-uuid/`)
	 * @param documentSpaceId doc space UUID
	 * @param prefix prefix from doc space root up to and including the folder to look under
	 * @return list of S3 objects (files)
	 */
	@Override
	public List<S3Object> getAllFilesInFolder(UUID documentSpaceId, String prefix) {
		List<S3Object> files = new ArrayList<>();
		getDocumentSpaceOrElseThrow(documentSpaceId);
		ListObjectsV2Request request = new ListObjectsV2Request().withBucketName(bucketName)
				.withPrefix(prefix);

		ListObjectsV2Result objectListing = documentSpaceClient.listObjectsV2(request);
		boolean hasNext = true;

		do {
			if (objectListing.getNextContinuationToken() == null) {
				hasNext = false;
			}

			List<S3ObjectSummary> fileSummaries = objectListing.getObjectSummaries();
			for (S3ObjectSummary summaryItem : fileSummaries) {
				files.add(documentSpaceClient.getObject(bucketName, summaryItem.getKey()));
			}

			if (hasNext) {
				request = request.withContinuationToken(objectListing.getNextContinuationToken());
				objectListing = documentSpaceClient.listObjectsV2(request);
			}
		} while (hasNext);

		return files;
	}

	@Override
	public List<String> getAllFilesInFolderSummaries(UUID documentSpaceId, String prefix) {
		return this.getAllFilesInFolder(documentSpaceId, prefix)
				.stream()
				.map(item -> item.getKey())
				.collect(Collectors.toList());
	}

	@Override
	public DocumentDto convertS3ObjectToDto(S3Object s3Object) {
		return DocumentDto.builder().key(s3Object.getKey()).path("").size(s3Object.getObjectMetadata().getContentLength())
				.uploadedBy("").uploadedDate(s3Object.getObjectMetadata().getLastModified()).build();
	}

	@Override
	public DocumentDto convertS3SummaryToDto(String documentSpacePathPrefix, S3ObjectSummary objSummary) {
		return DocumentDto.builder().key(objSummary.getKey().replace(documentSpacePathPrefix, ""))
				.path(documentSpacePathPrefix).size(objSummary.getSize()).uploadedBy("")
				.uploadedDate(objSummary.getLastModified()).build();
	}

	@Override
	public DocumentDetailsDto convertToDetailsDto(S3Object s3Object) {
		return DocumentDetailsDto.builder().key(s3Object.getKey()).path("")
				.size(s3Object.getObjectMetadata().getContentLength()).uploadedBy("")
				.uploadedDate(s3Object.getObjectMetadata().getLastModified()).metadata(s3Object.getObjectMetadata()).build();
	}

	@Override
	public DocumentSpace convertDocumentSpaceRequestDtoToEntity(DocumentSpaceRequestDto documentSpaceInfoDto) {
		return DocumentSpace.builder()
				.id(documentSpaceInfoDto.getId() == null ? UUID.randomUUID() : documentSpaceInfoDto.getId())
				.name(documentSpaceInfoDto.getName()).build();
	}

	@Override
	public DocumentSpaceResponseDto convertDocumentSpaceEntityToResponseDto(DocumentSpace documentSpace) {
		return DocumentSpaceResponseDto.builder().id(documentSpace.getId()).name(documentSpace.getName()).build();
	}

	private DocumentSpace getDocumentSpaceOrElseThrow(UUID documentSpaceId) throws RecordNotFoundException {
		Optional<DocumentSpace> optionalDocumentSpace = documentSpaceRepository.findById(documentSpaceId);

		return optionalDocumentSpace.orElseThrow(
				() -> new RecordNotFoundException(String.format("Document Space with id: %s not found", documentSpaceId)));
	}

	protected String createDocumentSpacePathPrefix(UUID documentSpaceId) {
		return documentSpaceId.toString() + "/";
	}

	/**
	 * Writes an S3 Object to the output stream. This will close the input stream of
	 * the S3 Object.
	 *
	 * @param zipOutputStream output stream
	 * @param s3Object        {@link S3Object} to write to output stream
	 */
	private void insertS3ObjectZipEntry(ZipOutputStream zipOutputStream, S3Object s3Object) {
		ZipEntry entry = new ZipEntry(s3Object.getKey());

		try (S3ObjectInputStream dataStream = s3Object.getObjectContent()) {
			zipOutputStream.putNextEntry(entry);

			dataStream.transferTo(zipOutputStream);

			zipOutputStream.closeEntry();
		} catch (IOException e) {
			log.warn("Failed to compress file: " + s3Object.getKey());
		}
	}

	@Override
	public void addDashboardUserToDocumentSpace(UUID documentSpaceId, DocumentSpaceDashboardMemberRequestDto documentSpaceDashboardMemberDto) throws RecordNotFoundException {
		DocumentSpace documentSpace = getDocumentSpaceOrElseThrow(documentSpaceId);

		DashboardUser dashboardUser = documentSpacePrivilegeService.createDashboardUserWithPrivileges(documentSpaceDashboardMemberDto.getEmail(), documentSpace,
				documentSpaceDashboardMemberDto.getPrivileges());

		documentSpace.addDashboardUser(dashboardUser);

		documentSpaceRepository.save(documentSpace);
	}

	@Override
	public void removeDashboardUserFromDocumentSpace(UUID documentSpaceId, String email) throws RecordNotFoundException {
		DocumentSpace documentSpace = getDocumentSpaceOrElseThrow(documentSpaceId);

		documentSpacePrivilegeService.removePrivilegesFromDashboardUser(email, documentSpace);

		DashboardUser dashboardUser = dashboardUserService.getDashboardUserByEmail(email);

		documentSpace.removeDashboardUser(dashboardUser);
		documentSpaceRepository.save(documentSpace);

		dashboardUser = dashboardUserService.getDashboardUserByEmail(email);

		Set<Privilege> privileges = dashboardUser.getPrivileges();

		if(privileges.size() == 1 && dashboardUser.getDocumentSpaces().size() == 0){
			Optional<Privilege> first = privileges.stream().findFirst();
			if(first.isPresent() && first.get().getName().equals("DASHBOARD_USER")){
				dashboardUserService.deleteDashboardUser(dashboardUser.getId());
			}
		}

	}

	@Override
	public Page<DocumentSpaceDashboardMemberResponseDto> getDashboardUsersForDocumentSpace(UUID documentSpaceId,
																						   @Nullable Pageable pageable) throws RecordNotFoundException {
		DocumentSpace documentSpace = getDocumentSpaceOrElseThrow(documentSpaceId);

		Page<DashboardUser> dashboardUsersPaged = dashboardUserRepository.findAllByDocumentSpaces_Id(documentSpace.getId(), pageable);
		Map<UUID, Integer> dashboardIdToIndexMap = new HashMap<>();
		for (int i = 0; i < dashboardUsersPaged.getContent().size(); i++) {
			dashboardIdToIndexMap.put(dashboardUsersPaged.getContent().get(i).getId(), i);
		}

		List<DocumentSpaceDashboardMemberPrivilegeRow> dashboardUserDocumentSpacePrivilegeRows =
				documentSpacePrivilegeService.getAllDashboardMemberPrivilegeRowsForDocumentSpace(documentSpace, dashboardIdToIndexMap.keySet());

		Map<DocumentSpaceDashboardMember, List<DocumentSpacePrivilegeDto>> map = dashboardUserDocumentSpacePrivilegeRows.stream()
				.collect(Collectors.groupingBy(DocumentSpaceDashboardMemberPrivilegeRow::getDashboardMember,
						Collectors.mapping(entry -> {
							DocumentSpacePrivilege privilege = entry.getPrivilege();

							return new DocumentSpacePrivilegeDto(privilege.getId(), privilege.getType());
						}, Collectors.toList())));

		List<DocumentSpaceDashboardMemberResponseDto> response = new ArrayList<>();
		map.entrySet().forEach(value -> response.add(new DocumentSpaceDashboardMemberResponseDto(value.getKey().getId(),
				value.getKey().getEmail(), value.getValue())));

		if (pageable != null && pageable.getSort().isSorted()) {
			response.sort(Comparator.comparingInt(item -> dashboardIdToIndexMap.get(item.getId())));
		}

		return new PageImpl<>(response, dashboardUsersPaged.getPageable(), response.size());
	}
}
