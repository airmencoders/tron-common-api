package mil.tron.commonapi.service.documentspace;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.s3.model.DeleteObjectsRequest.KeyVersion;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
import liquibase.util.csv.opencsv.CSVReader;
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
import mil.tron.commonapi.exception.NotAuthorizedException;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
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
import java.io.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@IfMinioEnabledOnStagingIL4OrDevLocal
public class DocumentSpaceServiceImpl implements DocumentSpaceService {
	protected static final String DOCUMENT_SPACE_USER_PRIVILEGE = "DOCUMENT_SPACE_USER";

	private final AmazonS3 documentSpaceClient;
	private final TransferManager documentSpaceTransferManager;
	private final String bucketName;

	private final DocumentSpaceRepository documentSpaceRepository;
	private final DashboardUserRepository dashboardUserRepository;

	private final DocumentSpacePrivilegeService documentSpacePrivilegeService;
	private final PrivilegeRepository privilegeRepository;

	@Value("${spring.profiles.active:UNKNOWN}")
	private String activeProfile;

	@Value("${ENCLAVE_LEVEL:UNKNOWN}")
	private String enclaveLevel;

	private final DashboardUserService dashboardUserService;

	@SuppressWarnings("squid:S00107")
	public DocumentSpaceServiceImpl(AmazonS3 documentSpaceClient, TransferManager documentSpaceTransferManager,
			@Value("${minio.bucket-name}") String bucketName, DocumentSpaceRepository documentSpaceRepository,
			DocumentSpacePrivilegeService documentSpacePrivilegeService,
			DashboardUserRepository dashboardUserRepository, DashboardUserService dashboardUserService,
			PrivilegeRepository privilegeRepository) {
		this.documentSpaceClient = documentSpaceClient;
		this.documentSpaceTransferManager = documentSpaceTransferManager;
		this.bucketName = bucketName;

		this.documentSpaceRepository = documentSpaceRepository;
		this.dashboardUserRepository = dashboardUserRepository;

		this.documentSpacePrivilegeService = documentSpacePrivilegeService;
		this.dashboardUserService = dashboardUserService;

		this.privilegeRepository = privilegeRepository;
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
	public S3Object getFile(UUID documentSpaceId, String key) throws RecordNotFoundException {
		DocumentSpace documentSpace = getDocumentSpaceOrElseThrow(documentSpaceId);

		return documentSpaceClient.getObject(bucketName, createDocumentSpacePathPrefix(documentSpace.getId()) + key);
	}

	@Override
	public S3Object downloadFile(UUID documentSpaceId, String fileKey) throws RecordNotFoundException {
		return getFile(documentSpaceId, fileKey);
	}

	@Override
	public List<S3Object> getFiles(UUID documentSpaceId, Set<String> fileKeys) throws RecordNotFoundException {
		DocumentSpace documentSpace = getDocumentSpaceOrElseThrow(documentSpaceId);

		return fileKeys.stream().map(
				item -> documentSpaceClient.getObject(bucketName, createDocumentSpacePathPrefix(documentSpace.getId()) + item))
				.collect(Collectors.toList());
	}

	@Override
	public void downloadAndWriteCompressedFiles(UUID documentSpaceId, Set<String> fileKeys, OutputStream out)
			throws RecordNotFoundException {
		List<S3Object> files = getFiles(documentSpaceId, fileKeys);

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
	public void uploadFile(UUID documentSpaceId, MultipartFile file) throws RecordNotFoundException {
		DocumentSpace documentSpace = getDocumentSpaceOrElseThrow(documentSpaceId);

		ObjectMetadata metaData = new ObjectMetadata();
		metaData.setContentType(file.getContentType());
		metaData.setContentLength(file.getSize());

		try {
			Upload upload = documentSpaceTransferManager.upload(bucketName,
					createDocumentSpacePathPrefix(documentSpace.getId()) + file.getOriginalFilename(), file.getInputStream(),
					metaData);

			upload.waitForCompletion();
		} catch (IOException | InterruptedException e) { // NOSONAR
			throw new BadRequestException("Failed retrieving input stream");
		}
	}

	@Override
	public void deleteFile(UUID documentSpaceId, String file) throws RecordNotFoundException {
		DocumentSpace documentSpace = getDocumentSpaceOrElseThrow(documentSpaceId);

		String fileKey = createDocumentSpacePathPrefix(documentSpace.getId()) + file;
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
	public S3PaginationDto listFiles(UUID documentSpaceId, String continuationToken, @Nullable Integer limit)
			throws RecordNotFoundException {
		DocumentSpace documentSpace = getDocumentSpaceOrElseThrow(documentSpaceId);

		if (limit == null) {
			limit = 20;
		}

		ListObjectsV2Request request = new ListObjectsV2Request().withBucketName(bucketName)
				.withPrefix(createDocumentSpacePathPrefix(documentSpace.getId())).withMaxKeys(limit)
				.withContinuationToken(continuationToken);

		ListObjectsV2Result objectListing = documentSpaceClient.listObjectsV2(request);
		List<S3ObjectSummary> summary = objectListing.getObjectSummaries();

		List<DocumentDto> documents = summary.stream()
				.map(item -> this.convertS3SummaryToDto(
						createDocumentSpacePathPrefix(documentSpace.getId()), documentSpace.getId(), item))
				.collect(Collectors.toList());

		return S3PaginationDto.builder().currentContinuationToken(objectListing.getContinuationToken())
				.nextContinuationToken(objectListing.getNextContinuationToken()).documents(documents).size(limit)
				.totalElements(documents.size()).build();
	}

	@Override
	public void downloadAllInSpaceAndCompress(UUID documentSpaceId, OutputStream out) throws RecordNotFoundException {
		DocumentSpace documentSpace = getDocumentSpaceOrElseThrow(documentSpaceId);

		try (BufferedOutputStream bos = new BufferedOutputStream(out); ZipOutputStream zipOut = new ZipOutputStream(bos);) {
			ListObjectsV2Request request = new ListObjectsV2Request().withBucketName(bucketName)
					.withPrefix(createDocumentSpacePathPrefix(documentSpace.getId()));

			ListObjectsV2Result objectListing = documentSpaceClient.listObjectsV2(request);
			boolean hasNext = true;

			do {
				if (objectListing.getNextContinuationToken() == null) {
					hasNext = false;
				}

				List<S3ObjectSummary> fileSummaries = objectListing.getObjectSummaries();

				for (int i = 0; i < fileSummaries.size(); i++) {
					S3ObjectSummary summaryItem = fileSummaries.get(i);

					S3Object s3Object = documentSpaceClient.getObject(bucketName, summaryItem.getKey());

					insertS3ObjectZipEntry(zipOut, s3Object);
				}

				if (hasNext) {
					request = request.withContinuationToken(objectListing.getNextContinuationToken());
					objectListing = documentSpaceClient.listObjectsV2(request);
				}
			} while (hasNext);

			zipOut.finish();
		} catch (IOException e1) {
			log.warn("Failure occurred closing zip output stream");
		}
	}

	@Override
	public DocumentDto convertS3ObjectToDto(S3Object s3Object) {
		return DocumentDto.builder().key(s3Object.getKey()).path("").size(s3Object.getObjectMetadata().getContentLength())
				.uploadedBy("").uploadedDate(s3Object.getObjectMetadata().getLastModified()).build();
	}

	@Override
	public DocumentDto convertS3SummaryToDto(String documentSpacePathPrefix, UUID documentSpaceId, S3ObjectSummary objSummary) {
		return DocumentDto.builder().key(objSummary.getKey().replace(documentSpacePathPrefix, ""))
				.path(documentSpacePathPrefix).size(objSummary.getSize()).uploadedBy("")
				.spaceId(documentSpaceId.toString())
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

	private void batchAddDashboardUserToDocumentSpace(DocumentSpace documentSpace, Set<DocumentSpaceDashboardMemberRequestDto> documentSpaceDashboardMemberDto) {

		Set<DashboardUser> dashBoardUsers = documentSpaceDashboardMemberDto.stream().map(member ->
				documentSpacePrivilegeService.createDashboardUserWithPrivileges(member.getEmail(), documentSpace, member.getPrivileges())).collect(Collectors.toSet());

		dashBoardUsers.forEach(documentSpace::addDashboardUser);

		documentSpaceRepository.save(documentSpace);
	}

	@Override
	public void removeDashboardUserFromDocumentSpace(UUID documentSpaceId, String email) throws RecordNotFoundException {
		DocumentSpace documentSpace = getDocumentSpaceOrElseThrow(documentSpaceId);

		documentSpacePrivilegeService.removePrivilegesFromDashboardUser(email, documentSpace);

		DashboardUser dashboardUser = dashboardUserService.getDashboardUserByEmail(email);

		if (dashboardUser == null) {
			throw new RecordNotFoundException(String.format("Could not remove user from Document Space space. User with email: %s does not exist", email));
		}

		documentSpace.removeDashboardUser(dashboardUser);
		documentSpaceRepository.save(documentSpace);

		Set<Privilege> privileges = dashboardUser.getPrivileges();

		if (dashboardUser.getDocumentSpaces().isEmpty()) {
			Optional<Privilege> documentSpaceGlobalPrivilege = privilegeRepository.findByName(DOCUMENT_SPACE_USER_PRIVILEGE);
			documentSpaceGlobalPrivilege.ifPresentOrElse(
					dashboardUser::removePrivilege,
					() -> log.error(String.format(
							"Could not remove Global Document Space Privilege (%s) from user because it is is missing",
							DOCUMENT_SPACE_USER_PRIVILEGE)));
		}

		if(privileges.size() == 1){
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

	@Override
	public List<DocumentSpacePrivilegeDto> getDashboardUserPrivilegesForDocumentSpace(UUID documentSpaceId,
			String dashboardUserEmail) throws RecordNotFoundException, NotAuthorizedException {
		DocumentSpace documentSpace = getDocumentSpaceOrElseThrow(documentSpaceId);
		DashboardUser dashboardUser = dashboardUserService.getDashboardUserByEmail(dashboardUserEmail);

		if (dashboardUser == null) {
			throw new RecordNotFoundException("Requesting Document Space Dashboard User does not exist with email: " + dashboardUserEmail);
		}

		List<DocumentSpaceDashboardMemberPrivilegeRow> dashboardUserDocumentSpacePrivilegeRows =
				documentSpacePrivilegeService.getAllDashboardMemberPrivilegeRowsForDocumentSpace(documentSpace, Set.of(dashboardUser.getId()));

		List<DocumentSpacePrivilegeDto> dashboardUserDocumentSpacePrivileges = dashboardUserDocumentSpacePrivilegeRows.stream().map(row -> {
			DocumentSpacePrivilege privilege = row.getPrivilege();
			return new DocumentSpacePrivilegeDto(privilege.getId(), privilege.getType());
		}).collect(Collectors.toList());

		if (dashboardUserDocumentSpacePrivileges.isEmpty()) {
			throw new NotAuthorizedException("Not Authorized to this Document Space");
		}

		return dashboardUserDocumentSpacePrivileges;
	}

	@Override
	public List<String> batchAddDashboardUserToDocumentSpace(UUID documentSpaceId, MultipartFile file) {

		Set<DocumentSpaceDashboardMemberRequestDto> membersToAdd = new HashSet<>();
		List<String> errorList = new ArrayList<>();

		DocumentSpace documentSpace = getDocumentSpaceOrElseThrow(documentSpaceId);
		Set<String> membersInSpace = documentSpace.getDashboardUsers().stream().map(DashboardUser::getEmail).collect(Collectors.toSet());

		try (CSVReader csvReader = new CSVReader(new InputStreamReader(file.getInputStream()))) {

			List<String[]> strings = csvReader.readAll();
			for (int i = 0; i < strings.size(); i++) {
				if(i == 0){
					validateCSVHeader(strings.get(i), errorList);
				}else if(strings.get(i).length != 0){
					DocumentSpaceDashboardMemberRequestDto newDashboardSpaceMember = processCSVRow(strings.get(i), i, errorList);
					if(newDashboardSpaceMember.getEmail() == null){ // case where an error was found
						continue;
					}else if(membersInSpace.contains(newDashboardSpaceMember.getEmail())){ // case for email exists already
						errorList.add(String.format("Unable to add user with email %s, they are already a part of the space", newDashboardSpaceMember.getEmail()));
					}else if(membersToAdd.stream().anyMatch(m->m.getEmail().equals(newDashboardSpaceMember.getEmail()))){ // case where email was found in the csv previously
						errorList.add("Duplicate email found on row " + (i + 1));
					}else{
						membersToAdd.add(newDashboardSpaceMember);
					}
				}
			}
		} catch (IOException e) {
			throw new BadRequestException("Failed retrieving uploaded file");
		}

		if(errorList.isEmpty()){
			batchAddDashboardUserToDocumentSpace(documentSpace, membersToAdd);
		}

		return  errorList;
	}

	private void validateCSVHeader(String[] row, List<String> errorList){

		if(!row[0].trim().equalsIgnoreCase("email")){
			errorList.add("Improper first CSV header: email");
		}else if(!row[1].trim().equalsIgnoreCase(DocumentSpacePrivilegeType.READ.toString())){
			errorList.add("Improper second CSV header: read");
		}else if(!row[2].trim().equalsIgnoreCase(DocumentSpacePrivilegeType.WRITE.toString())){
			errorList.add("Improper third CSV header: write");
		}else if(!row[3].trim().equalsIgnoreCase(DocumentSpacePrivilegeType.MEMBERSHIP.toString())){
			errorList.add("Improper fourth CSV header: membership");
		}
	}

	private DocumentSpaceDashboardMemberRequestDto processCSVRow(String[] row, int i, List<String> errorList) {
		int rowLength = row.length;
		DocumentSpaceDashboardMemberRequestDto memberToAdd = DocumentSpaceDashboardMemberRequestDto.builder().privileges(new ArrayList<>()).build();
		if (rowLength < 2) {
			errorList.add("Improper minimum row length on row " + (i + 1));
			return memberToAdd;
		}

		String email = row[0].trim();
		if(email.equals("")){
			errorList.add("Missing email on row " + (i + 1));
			return memberToAdd;
		}else{
			memberToAdd.setEmail(email);
		}


		if (parseBooleanPrivilegeValue(row[1].trim())) {
			memberToAdd.getPrivileges().add(DocumentSpacePrivilegeType.READ);
		}
		if (rowLength >= 3 && parseBooleanPrivilegeValue(row[2].trim())) {
			memberToAdd.getPrivileges().add(DocumentSpacePrivilegeType.WRITE);
		}
		if (rowLength >= 4 && parseBooleanPrivilegeValue(row[3].trim())) {
			memberToAdd.getPrivileges().add(DocumentSpacePrivilegeType.MEMBERSHIP);
		}


		return memberToAdd;
	}

	/**
	 * Parse possible string values as a boolean
	 * @param privilege boolean input value as a string
	 * @return boolean true if string is valid and parsed as true
	 * 			else returns false
	 */
	private boolean parseBooleanPrivilegeValue(String privilege){
		return privilege.equalsIgnoreCase("true") || privilege.equalsIgnoreCase("yes") || privilege.equals("1");
	}
}
