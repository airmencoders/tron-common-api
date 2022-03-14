package mil.tron.commonapi.service.documentspace;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.s3.model.MultiObjectDeleteException.DeleteError;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
import liquibase.util.csv.opencsv.CSVReader;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import mil.tron.commonapi.annotation.minio.IfMinioEnabledOnIL4OrDevLocal;
import mil.tron.commonapi.dto.documentspace.*;
import mil.tron.commonapi.dto.documentspace.mobile.DocumentMobileDto;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.documentspace.*;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.exception.NotAuthorizedException;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceRepository;
import mil.tron.commonapi.service.DashboardUserService;
import mil.tron.commonapi.service.documentspace.util.FilePathSpec;
import mil.tron.commonapi.service.documentspace.util.FilePathSpecWithContents;
import mil.tron.commonapi.service.documentspace.util.FileSystemElementTree;
import mil.tron.commonapi.service.documentspace.util.S3ObjectAndFilename;
import mil.tron.commonapi.validations.DocSpaceFolderOrFilenameValidator;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FilenameUtils;
import org.assertj.core.util.Lists;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.transaction.Transactional;
import javax.validation.constraints.NotNull;
import java.io.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static mil.tron.commonapi.service.DashboardUserServiceImpl.DASHBOARD_ADMIN_PRIV;
import static mil.tron.commonapi.service.DashboardUserServiceImpl.DASHBOARD_USER_PRIV;
import static mil.tron.commonapi.service.documentspace.DocumentSpaceFileSystemServiceImpl.joinPathParts;

@Slf4j
@Service
@IfMinioEnabledOnIL4OrDevLocal
public class DocumentSpaceServiceImpl implements DocumentSpaceService {
	public static final String DOCUMENT_SPACE_USER_PRIVILEGE = "DOCUMENT_SPACE_USER";
	
	private final AmazonS3 documentSpaceClient;
	private final TransferManager documentSpaceTransferManager;
	private final String bucketName;

	private final DocumentSpaceRepository documentSpaceRepository;
	private final DashboardUserRepository dashboardUserRepository;
	private final DocumentSpaceFileSystemService documentSpaceFileSystemService;

	private final DocumentSpacePrivilegeService documentSpacePrivilegeService;
	private final PrivilegeRepository privilegeRepository;
	
	private final DocumentSpaceFileService documentSpaceFileService;
	private final DocumentSpaceMetadataService metadataService;

	@Value("${spring.profiles.active:UNKNOWN}")
	private String activeProfile;

	@Value("${ENCLAVE_LEVEL:UNKNOWN}")
	private String enclaveLevel;

	private final DashboardUserService dashboardUserService;
	private final DocumentSpaceUserCollectionService documentSpaceUserCollectionService;

	@SuppressWarnings("squid:S00107")
	public DocumentSpaceServiceImpl(AmazonS3 documentSpaceClient, TransferManager documentSpaceTransferManager,
			@Value("${minio.bucket-name}") String bucketName, DocumentSpaceRepository documentSpaceRepository,
			DocumentSpacePrivilegeService documentSpacePrivilegeService,
			DashboardUserRepository dashboardUserRepository, DashboardUserService dashboardUserService,
			PrivilegeRepository privilegeRepository, DocumentSpaceFileSystemService documentSpaceFileSystemService,
			DocumentSpaceFileService documentSpaceFileService, DocumentSpaceMetadataService metadataService,
			DocumentSpaceUserCollectionService documentSpaceUserCollectionService) {

		this.documentSpaceClient = documentSpaceClient;
		this.documentSpaceTransferManager = documentSpaceTransferManager;
		this.bucketName = bucketName;

		this.documentSpaceRepository = documentSpaceRepository;
		this.dashboardUserRepository = dashboardUserRepository;

		this.documentSpaceFileSystemService = documentSpaceFileSystemService;

		this.documentSpacePrivilegeService = documentSpacePrivilegeService;
		this.dashboardUserService = dashboardUserService;

		this.privilegeRepository = privilegeRepository;
		
		this.documentSpaceFileService = documentSpaceFileService;
		this.metadataService = metadataService;

		this.documentSpaceUserCollectionService = documentSpaceUserCollectionService;
	}

	@Override
	public List<DocumentSpaceResponseDto> listSpaces(String username) {
		DashboardUser dashboardUser = dashboardUserService.getDashboardUserByEmailAsLower(username);
		
		if (dashboardUser == null) {
			throw new RecordNotFoundException(String.format("Could not find spaces. User: %s does not exist", username));
		}
		
		if (dashboardUser.getPrivileges().stream().anyMatch(privilege -> privilege.getName().equalsIgnoreCase(DASHBOARD_ADMIN_PRIV))) {
			return documentSpaceRepository.findAllDynamicBy(DocumentSpaceResponseDto.class);
		}
		
		return documentSpaceRepository.findAllDynamicByDashboardUsers_Id(dashboardUser.getId(), DocumentSpaceResponseDto.class);
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

	@Transactional
	@Override
	public void deleteSpace(UUID documentSpaceId) throws RecordNotFoundException {
		DocumentSpace documentSpace = getDocumentSpaceOrElseThrow(documentSpaceId);

		documentSpaceFileSystemService.deleteFolder(documentSpaceId, DocumentSpaceFileSystemServiceImpl.PATH_SEP);

		unsetDashboardUsersDefaultDocumentSpace(documentSpace);
		
		documentSpacePrivilegeService.deleteAllPrivilegesBelongingToDocumentSpace(documentSpace);
		documentSpaceRepository.deleteById(documentSpace.getId());

	}

	private String getPathPrefix(UUID documentSpaceId, String path, FilePathSpec spec) {
		return !path.isBlank() && !spec.getDocSpaceQualifiedPath().isBlank()
				? spec.getDocSpaceQualifiedPath()
				: this.createDocumentSpacePathPrefix(documentSpaceId);
	}

	@Override
	public S3Object getFile(UUID documentSpaceId, String path, String key, String documentSpaceUsername)
			throws RecordNotFoundException {
		DashboardUser dashboardUser = getDashboardUserOrElseThrow(documentSpaceUsername);

		FilePathSpec spec = documentSpaceFileSystemService.parsePathToFilePathSpec(documentSpaceId, path);
		String prefix = getPathPrefix(documentSpaceId, spec.getFullPathSpec(), spec);

		DocumentSpaceFileSystemEntry fileEntry = documentSpaceFileService
				.getFileInDocumentSpaceFolderOrThrow(documentSpaceId, spec.getItemId(), key);
		DocumentMetadata metadata = new DocumentMetadata(new Date());
		metadataService.saveMetadata(documentSpaceId, fileEntry, metadata, dashboardUser);

		return getS3Object(prefix + key);
	}
	
	@Override
	public S3Object getFile(UUID documentSpaceId, UUID parentFolderId, String filename, String documentSpaceUsername)
			throws RecordNotFoundException {
		DashboardUser dashboardUser = getDashboardUserOrElseThrow(documentSpaceUsername);

		FilePathSpec filePathSpec = documentSpaceFileSystemService.getFilePathSpec(documentSpaceId, parentFolderId);

		DocumentSpaceFileSystemEntry fileEntry = documentSpaceFileService
				.getFileInDocumentSpaceFolderOrThrow(documentSpaceId, parentFolderId, filename);
		DocumentMetadata metadata = new DocumentMetadata(new Date());
		metadataService.saveMetadata(documentSpaceId, fileEntry, metadata, dashboardUser);

		return getS3Object(getPathPrefix(documentSpaceId, filePathSpec.getFullPathSpec(), filePathSpec) + filename);
	}

	/**
	 * Gets files (multiple) from the same document space folder
	 * @param documentSpaceId document space UUID
	 * @param path the path (the folder from which to download the files from)
	 * @param fileKeys the selected files from the folder user wants downloaded
	 * @return the S3 objects representing said files
	 * @throws RecordNotFoundException
	 */
	@Override
	public List<S3Object> getFiles(UUID documentSpaceId, String path, Set<String> fileKeys, String documentSpaceUsername)
			throws RecordNotFoundException {
		DashboardUser dashboardUser = getDashboardUserOrElseThrow(documentSpaceUsername);

		FilePathSpec spec = documentSpaceFileSystemService.parsePathToFilePathSpec(documentSpaceId, path);
		String prefix = getPathPrefix(documentSpaceId, spec.getFullPathSpec(), spec);

		Set<DocumentSpaceFileSystemEntry> entriesToWriteMetadata = new HashSet<>();
		DocumentMetadata metadata = new DocumentMetadata(new Date());

		List<S3Object> s3Objects = fileKeys.stream().map(item -> {
			DocumentSpaceFileSystemEntry fileEntry = documentSpaceFileService
					.getFileInDocumentSpaceFolderOrThrow(documentSpaceId, spec.getItemId(), item);
			entriesToWriteMetadata.add(fileEntry);
			return getS3Object(prefix + item);
		}).collect(Collectors.toList());

		metadataService.saveMetadata(documentSpaceId, entriesToWriteMetadata, metadata, dashboardUser);

		return s3Objects;
	}
	
	private S3Object getS3Object(String key) throws RecordNotFoundException {
		if (documentSpaceClient.doesObjectExist(bucketName, key)) {
			return documentSpaceClient.getObject(bucketName, key);
		}
		else {
			throw new RecordNotFoundException("That file does not exist");
		}
	}

	/**
	 * Writes chosen files from the same doc space folder - into a downloadable zip file
	 * @param documentSpaceId the document space UUID
	 * @param path the plain-english path of the file in relation to the doc space
	 * @param fileKeys the list of filenames from this folder to zip up
	 * @param out the zip outstream that sends contents to the client
	 * @throws RecordNotFoundException
	 */
	@Override
	public void downloadAndWriteCompressedFiles(UUID documentSpaceId, String path, Set<String> fileKeys, OutputStream out,
			String documentSpaceUsername)
			throws RecordNotFoundException {
		DashboardUser dashboardUser = getDashboardUserOrElseThrow(documentSpaceUsername);

		// make sure given path starts with a "/"
		String searchPath = (path.startsWith(DocumentSpaceFileSystemServiceImpl.PATH_SEP) ? "" : DocumentSpaceFileSystemServiceImpl.PATH_SEP) + path;

		// Get everything at the relative root path
		FilePathSpecWithContents contentsAtRelativeRoot = documentSpaceFileSystemService
				.getFilesAndFoldersAtPath(documentSpaceId, searchPath);

		// Filter only the items that are requested at the relative root path
		// Then collect items based on folder status
		Map<Boolean, List<DocumentSpaceFileSystemEntry>> entriesFilteredByRelevantAndMappedByFolder = contentsAtRelativeRoot
				.getEntries().stream().filter(entry -> fileKeys.contains(entry.getItemName()))
				.collect(Collectors.groupingBy(DocumentSpaceFileSystemEntry::isFolder));

		List<S3ObjectAndFilename> itemsToWrite = new ArrayList<>();
		Set<DocumentSpaceFileSystemEntry> entriesToWriteMetadata = new HashSet<>();

		// For each folder at the relative root, dump the contents
		List<DocumentSpaceFileSystemEntry> folders = entriesFilteredByRelevantAndMappedByFolder.get(true);
		if (folders != null) {
			for (DocumentSpaceFileSystemEntry folderEntry : folders) {
				FilePathSpec filePathSpec = documentSpaceFileSystemService.getFilePathSpec(documentSpaceId,
						folderEntry.getItemId());
				FileSystemElementTree contentsAtPath = documentSpaceFileSystemService.dumpElementTree(documentSpaceId,
						filePathSpec.getFullPathSpec(), false);
				List<S3ObjectAndFilename> flatTree = documentSpaceFileSystemService
						.flattenTreeToS3ObjectAndFilenameList(contentsAtPath);

				itemsToWrite.addAll(flatTree);
			}

			// Add only the selected folders for metadata update
			entriesToWriteMetadata.addAll(folders);
		}

		// Create S3ObjectAndFilename for the individual files at the relative root
		List<DocumentSpaceFileSystemEntry> files = entriesFilteredByRelevantAndMappedByFolder.get(false);
		if (files != null) {
			List<S3ObjectSummary> s3summary = getAllFilesInFolder(documentSpaceId, searchPath, false);

			for (DocumentSpaceFileSystemEntry fileEntry : files) {
				Optional<S3ObjectSummary> summary = s3summary.stream().filter(
						entry -> entry.getKey().equals(contentsAtRelativeRoot.getDocSpaceQualifiedPath() + fileEntry.getItemName()))
						.findAny();

				if (summary.isPresent()) {
					S3ObjectAndFilename s3ObjAndFilename = new S3ObjectAndFilename(joinPathParts(contentsAtRelativeRoot.getFullPathSpec(), fileEntry.getItemName()),
							summary.get());
					itemsToWrite.add(s3ObjAndFilename);
				}
			}

			// add selected file for metadata update
			entriesToWriteMetadata.addAll(files);
		}

		// update metadata
		DocumentMetadata metadata = new DocumentMetadata(new Date());
		metadataService.saveMetadata(documentSpaceId, entriesToWriteMetadata, metadata, dashboardUser);

		writeZipFile(out, itemsToWrite);
	}

	@Override
	public void uploadFile(UUID documentSpaceId, String path, MultipartFile file) {
		// overload for when the last modified date isn't given
		this.uploadFile(documentSpaceId, path, file, new Date());
	}

	@Override
	public void uploadFile(UUID documentSpaceId, String path, MultipartFile file, @NotNull Date lastModified) throws RecordNotFoundException {
		getDocumentSpaceOrElseThrow(documentSpaceId);

		// we need to build these out here because on a folder upload, the file "names" will actually contain paths
		//  relative to wherever the user currently is in the tree (so "path" from now one is really 'path' (the arg) + 'additionalPath' extracted from filename)
		//  if its just a file, no harm no foul, the 'additionalPath' is just blank
		String additionalPath = FilenameUtils.getPath(file.getOriginalFilename());
		String filename = FilenameUtils.getName(file.getOriginalFilename());

		DocSpaceFolderOrFilenameValidator validator = new DocSpaceFolderOrFilenameValidator();
		if (!validator.isValid(filename, null)) {
			throw new BadRequestException("Invalid filename");
		}

		// get the location to which we want to upload this file too - and create folders if they don't exist
		FilePathSpec filePathSpec = documentSpaceFileSystemService.parsePathToFilePathSpec(documentSpaceId,
				joinPathParts(path, additionalPath),
				true);

		String prefix = getPathPrefix(documentSpaceId,
				joinPathParts(path, additionalPath),
				filePathSpec);
		
		ObjectMetadata metaData = new ObjectMetadata();
		metaData.setContentType(file.getContentType());
		metaData.setLastModified(lastModified);  // attempt to preserve file last modified date
		metaData.setContentLength(file.getSize());

		// but also stash the last modified (as epoch milli) into metadata - S3 does not let you set the last modified date
		// but rather uses the date it was uploaded/changed from thereon.. but helps us in no way for wanting to upload
		// older files from someones computer - we'll attempt to fetch this value too when writing out ZipFileEntries so as
		// to make sure the date is preserved to the downloaded/unzipped item
		metaData.setUserMetadata(Map.of("lastModified", String.valueOf(lastModified.toInstant().toEpochMilli())));

		if (filename == null) {
			throw new BadRequestException("Uploaded file is missing a filename");
		}
		
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e1) {
			throw new IllegalArgumentException("Internal error occurred while uploading file: could not generate checksum value");
		}
		
		try (BufferedInputStream bis = new BufferedInputStream(file.getInputStream());
			 			DigestInputStream dis = new DigestInputStream(bis, md)) {
			DocumentSpaceFileSystemEntry documentSpaceFile = documentSpaceFileService
					.getFileInDocumentSpaceFolder(documentSpaceId, filePathSpec.getItemId(), filename).orElse(null);

			// for now we don't allow uploading of a file who has same name/path of an item that
			//  has archived status... because the archived file system entry and this new one would point to
			//  the same physical file in S3 bucket, thereby not allowing any restoration of the archived file (since this action could overwrite)
			if (documentSpaceFile != null && documentSpaceFile.isDeleteArchived()) {
				throw new ResourceAlreadyExistsException("A file with that name and path is in an archived state, purge archived version if upload is desired");
			}

			Upload upload = documentSpaceTransferManager.upload(bucketName,prefix + filename, bis, metaData);
			upload.waitForCompletion();
			
			if (documentSpaceFile == null) {
				documentSpaceFile = DocumentSpaceFileSystemEntry.builder()
						.documentSpaceId(documentSpaceId)
						.parentEntryId(filePathSpec.getItemId())
						.isFolder(false)
						.itemName(filename)
						.lastModifiedOn(lastModified)
						.size(file.getSize())
						.etag(Hex.encodeHexString(md.digest()))
						.isDeleteArchived(false)
						.build();
			} else {
				documentSpaceFile.setSize(file.getSize());
				documentSpaceFile.setLastModifiedOn(lastModified);
				documentSpaceFile.setEtag(Hex.encodeHexString(md.digest()));
			}
			
			documentSpaceFileService.saveDocumentSpaceFile(documentSpaceFile);
			documentSpaceFileSystemService.propagateModificationStateToAncestors(documentSpaceFile);
		} catch (IOException | InterruptedException e) { // NOSONAR
			throw new BadRequestException("Failed retrieving input stream");
		}
	}

	@Transactional(dontRollbackOn={RecordNotFoundException.class})
	@Override
	public void renameFile(UUID documentSpaceId, String path, String fileKey, String newName) {
		getDocumentSpaceOrElseThrow(documentSpaceId);
		FilePathSpec filePathSpec = documentSpaceFileSystemService.parsePathToFilePathSpec(documentSpaceId, path);
		String prefix = getPathPrefix(documentSpaceId, path, filePathSpec);

		DocumentSpaceFileSystemEntry documentSpaceFile = documentSpaceFileService
				.getFileInDocumentSpaceFolder(documentSpaceId, filePathSpec.getItemId(), fileKey).orElse(null);

		String s3FileKey = prefix + fileKey.trim();
		String newS3FileKey = prefix + newName.trim();

		if (documentSpaceFile == null) {
			log.warn("Could not rename Document Space File: it does not exist in the database");
		} else {
			if (!s3FileKey.equals(newS3FileKey)) {
				// we can allow renaming to same exact name (just like the Unix touch command)... but we dont
				//  actually need to do anything for the name operation because it'll trigger a 409 error
				documentSpaceFileService.renameDocumentSpaceFile(documentSpaceFile, newName);
			}

			// touch the file mod date/time
			documentSpaceFileSystemService.propagateModificationStateToAncestors(documentSpaceFile);
		}

		// copy to new name at the same path
		documentSpaceClient.copyObject(this.bucketName, s3FileKey, this.bucketName, newS3FileKey);

		// delete the old file - only if we didn't just happen to copy the file back on to itself
		//  otherwise we'd end up with no file at all
		if (!fileKey.equals(newName)) {
			this.deleteS3ObjectByKey(s3FileKey);
		}
	}

	/**
	 * Deletes items - be it folders and/or files - from the currentPath
	 * @param documentSpaceId document space Id
	 * @param currentPath current path we're at (e.g. in the UI)
	 * @param items the items in this current path to delete
	 */
	@Transactional
	@Override
	public void deleteItems(UUID documentSpaceId, String currentPath, List<String> items) {
		for (String item : items) {
			if (documentSpaceFileSystemService.isFolder(documentSpaceId, currentPath, item)) {
				documentSpaceFileSystemService.deleteFolder(documentSpaceId, FilenameUtils.concat(currentPath, item));
			}
			else {
				try {
					this.deleteFile(documentSpaceId, currentPath, item);
				}
				catch (RecordNotFoundException ex) {
					log.warn(String.format("Could not delete file: %s at path %s", currentPath, item));

					// throw so we rollback the operation
					throw new RecordNotFoundException(String.format("Could not delete file: %s at path %s", currentPath, item));
				}
			}
		}
	}

	/**
	 * Moves file from one location to another within given space
	 * @param documentSpaceId
	 * @param sourceSpaceId (optionally null) the source space where we're moving from (if null assumes documentSpaceId is source)
	 * @param source
	 * @param dest
	 */
	@Override
	public void moveFile(UUID documentSpaceId, @javax.annotation.Nullable UUID sourceSpaceId, String source, String dest) {
		if (sourceSpaceId == null) {
			sourceSpaceId = documentSpaceId;
		}

		this.moveOrCopyFile(documentSpaceId, sourceSpaceId, source, dest, false);
	}

	/**
	 * Copies file from one location to another within given space
	 * @param documentSpaceId
	 * @param sourceSpaceId (optionally null) the source space where we're copying from (if null assumes documentSpaceId is source)
	 * @param source
	 * @param dest
	 */
	@Override
	public void copyFile(UUID documentSpaceId, @javax.annotation.Nullable UUID sourceSpaceId, String source, String dest) {
		if (sourceSpaceId == null) {
			sourceSpaceId = documentSpaceId;
		}

		this.moveOrCopyFile(documentSpaceId, sourceSpaceId, source, dest, true);
	}

	/**
	 * Move or copies a file or folder from one location to another within the given space
	 * @param destinationSpaceId - document space UUID we're doing this operation in (the destination for copying or moving)
	 * @param sourceSpaceId - source space files are coming from
	 * @param source - the source path string (including path and final file or folder name)
	 * @param dest - the destination path string (including path and final file or folder name)
	 * @param copy - true if this is a copy operation
	 */
	@Transactional
	@Override
	public void moveOrCopyFile(UUID destinationSpaceId, UUID sourceSpaceId, String source, String dest, boolean copy) {
		// first make sure the source file exists and get a handle to its file system entry
		FilePathSpec sourceSpecification = documentSpaceFileSystemService.parsePathToFilePathSpec(sourceSpaceId, source);
		DocumentSpaceFileSystemEntry sourceEntry = documentSpaceFileSystemService.getElementByItemId(sourceSpecification.getItemId());

		// break down the source into its path and name components
		String sourcePath = FilenameUtils.getPath(source);
		String sourceFileName = FilenameUtils.getName(source);

		// break down the destination into its path and name components
		String destPath = FilenameUtils.getPath(dest);
		String destItemName = FilenameUtils.getName(dest);

		// validate the new destination name
		DocSpaceFolderOrFilenameValidator validator = new DocSpaceFolderOrFilenameValidator();
		if (!validator.isValid(destItemName, null)) {
			throw new BadRequestException("Invalid filename");
		}

		// check that we're not copying or writing over onto the source itself (within the same space)
		if (sourceSpaceId.equals(destinationSpaceId) && sourcePath.equals(destPath) && sourceFileName.equals(destItemName) && !copy) {
			throw new BadRequestException("Cannot move file onto itself - choose different path or destination name");
		}

		// get the location in the space to which we want to move this file too - and create folders if they don't exist
		//  any new folders created will have their names validated within the method called here
		FilePathSpec destinationSpecification = documentSpaceFileSystemService
				.parsePathToFilePathSpec(destinationSpaceId, destPath, true);

		// see if there's an item that exists already at the destination
		Optional<DocumentSpaceFileSystemEntry> existingElement = documentSpaceFileSystemService
				.getByParentIdAndItemName(destinationSpaceId, destinationSpecification.getItemId(), destItemName);

		// check destination for like-named item, generate a new one with "Copy" suffix if needed
		if (copy) {
			destItemName = generateCopyFileName(destinationSpaceId, destItemName, destinationSpecification);
		}

		if (!copy) {
			// if moving..
			moveDbItems(destinationSpaceId, sourceEntry, dest, destItemName, sourceSpecification, destinationSpecification, existingElement);
		} else {
			// if copying...
			copyDbItems(destinationSpaceId, sourceEntry, destItemName, sourceSpecification, destinationSpecification);
		}
	}

	/**
	 * Little helper to generate a new file name for copy names that conflict
	 * @param destinationSpaceId
	 * @param destItemName
	 * @param destinationSpecification
	 * @return
	 */
	private String generateCopyFileName(UUID destinationSpaceId, String destItemName, FilePathSpec destinationSpecification) {
		String fileName = FilenameUtils.getBaseName(destItemName);
		String ext = FilenameUtils.getExtension(destItemName);
		int countCandidate = 1;
		final int maxCopies = 1000;

		// no existing filename exists, no need to mangle it
		if (documentSpaceFileSystemService
				.getByParentIdAndItemName(destinationSpaceId, destinationSpecification.getItemId(), destItemName).isEmpty()) {
			return destItemName;
		}

		// iterate thru the copy numbers to get a suitable suffix to add to the file name
		do {
			String possibleName = fileName + " - (Copy " + countCandidate + ")" + (ext.isBlank() ? "" : "." + ext);
			if (documentSpaceFileSystemService
					.getByParentIdAndItemName(destinationSpaceId, destinationSpecification.getItemId(), possibleName).isEmpty()) {
				destItemName = possibleName;
				break;
			} else {
				countCandidate++;
			}
		} while (countCandidate < maxCopies);

		if (countCandidate >= maxCopies) {
			throw new BadRequestException("Could not copy file - too many copies of same file present");
		}
		return destItemName;
	}

	/**
	 * Private helper to manage the onerous task of copying db entries and s3 objects for a file/folder COPY operation
	 * @param destinationSpaceId
	 * @param sourceEntry
	 * @param destItemName
	 * @param sourceSpecification
	 * @param destinationSpecification
	 */
	private void copyDbItems(UUID destinationSpaceId, DocumentSpaceFileSystemEntry sourceEntry, String destItemName,
							 FilePathSpec sourceSpecification, FilePathSpec destinationSpecification) {

		// create a new DocumentSpaceFileSystemEntry..
		DocumentSpaceFileSystemEntry destEntry = new DocumentSpaceFileSystemEntry();

		destEntry.setDocumentSpaceId(destinationSpaceId);
		destEntry.setItemId(UUID.randomUUID());
		destEntry.setItemName(destItemName);
		destEntry.setEtag(sourceEntry.getEtag());
		destEntry.setFolder(sourceEntry.isFolder());
		destEntry.setParentEntryId(destinationSpecification.getItemId());
		destEntry.setLastModifiedOn(sourceEntry.getLastModifiedOn());
		destEntry.setDeleteArchived(sourceEntry.isDeleteArchived());
		destEntry.setSize(sourceEntry.getSize());
		destEntry.setHasNonArchivedContents(sourceEntry.isHasNonArchivedContents());
		documentSpaceFileSystemService.saveItem(destEntry);

		// must copy all the db system entries that were attached to the original element
		// so that we can duplicate everything contained therein (if it had children, e.g. it was a folder) within the filesystem database
		// IF we don't do this, all our item "copies" contained within the new folder will actually "point" back to the original
		// file... so basically have two file system entries pointing to the same physical file - which is no bueno
		documentSpaceFileSystemService.duplicateFileSystemEntryTree(destinationSpaceId, sourceEntry, destEntry.getItemId());

		// done with the db mayhem, now for the S3, physical file stuff
		if (!sourceEntry.isFolder()) {
			// since there is no "rename" feature where we can just renamed a key or a portion of a key we must...
			// get the S3 object and change its prefix/key by COPYING it to new home - then deleting the old
			CopyObjectRequest copyObjRequest = new CopyObjectRequest(
					bucketName,
					sourceSpecification.getDocSpaceQualifiedFilePath(),
					bucketName,
					destinationSpecification.getDocSpaceQualifiedPath() + destItemName);

			documentSpaceClient.copyObject(copyObjRequest);
		} else {
			// if its a folder, little more complicated - we need to copy all the elements with a same
			// prefix to the new prefix
			ListObjectsV2Request request = new ListObjectsV2Request()
					.withBucketName(bucketName)
					.withPrefix(sourceSpecification.getDocSpaceQualifiedPath());

			ListObjectsV2Result items = documentSpaceClient.listObjectsV2(request);
			for (val item : items.getObjectSummaries()) {

				FilePathSpec spec = documentSpaceFileSystemService.convertFileSystemEntityToFilePathSpec(destEntry);

				// formulate the copy request to copy the existing key in S3
				CopyObjectRequest copyObjRequest = new CopyObjectRequest(
						bucketName,
						item.getKey(),
						bucketName,
						spec.getDocSpaceQualifiedPath() + item.getKey().replaceFirst(sourceSpecification.getDocSpaceQualifiedPath(), ""));

				documentSpaceClient.copyObject(copyObjRequest);
			}
		}
	}

	/**
	 * Private helper to manage the onerous task of moving db entries and s3 objects for a file/folder MOVE operation
	 * @param destinationSpaceId
	 * @param sourceEntry
	 * @param destItemName
	 * @param sourceSpecification
	 * @param destinationSpecification
	 * @param existingElement
	 */
	private void moveDbItems(UUID destinationSpaceId, DocumentSpaceFileSystemEntry sourceEntry, String destinationPath, String destItemName,
							 FilePathSpec sourceSpecification, FilePathSpec destinationSpecification, Optional<DocumentSpaceFileSystemEntry> existingElement) {

		// change the source element's parent Id to its new home (effectively "deleting" it from its old location in the db)
		// update the db file system entry with new parent/new name
		if (existingElement.isPresent()) {
			// moving over existing item... delete the existing entry first from the database
			this.deleteItems(destinationSpaceId, destinationSpecification.getFullPathSpec(), Lists.newArrayList(destItemName));
		}

		// must modify all the db system entries that were attached to the original element
		// to have the document space of the destination as their owning document space now...
		// otherwise we'll orphan everything that was underneath destEntry
		documentSpaceFileSystemService.moveFileSystemEntryTree(destinationSpaceId, sourceEntry, sourceEntry.getItemId());

		// reparent the top-level item of the move (change its parent and document space id [may or may not have changed])
		sourceEntry.setDocumentSpaceId(destinationSpaceId);
		sourceEntry.setParentEntryId(destinationSpecification.getItemId());
		sourceEntry.setItemName(destItemName);
		documentSpaceFileSystemService.saveItem(sourceEntry);

		if (!sourceEntry.isFolder()) {
			// since there is no "rename" feature where we can just renamed a key or a portion of a key we must...
			// get the S3 object and change its prefix/key by COPYING it to new home - then deleting the old
			CopyObjectRequest copyObjRequest = new CopyObjectRequest(
					bucketName,
					sourceSpecification.getDocSpaceQualifiedFilePath(),
					bucketName,
					destinationSpecification.getDocSpaceQualifiedPath() + destItemName);

			documentSpaceClient.copyObject(copyObjRequest);
			documentSpaceClient.deleteObject(new DeleteObjectRequest(bucketName, sourceSpecification.getDocSpaceQualifiedFilePath()));
		} else {
			// if its a folder, little more complicated - we need to copy all the elements with a same
			// prefix to the new prefix
			ListObjectsV2Request request = new ListObjectsV2Request()
					.withBucketName(bucketName)
					.withPrefix(sourceSpecification.getDocSpaceQualifiedPath());

			ListObjectsV2Result items = documentSpaceClient.listObjectsV2(request);
			for (val item : items.getObjectSummaries()) {

				FilePathSpec spec = documentSpaceFileSystemService.parsePathToFilePathSpec(destinationSpaceId, destinationPath);

				// formulate the copy request to copy the existing key in S3
				CopyObjectRequest copyObjRequest = new CopyObjectRequest(
						bucketName,
						item.getKey(),
						bucketName,
						spec.getDocSpaceQualifiedPath() + item.getKey().replaceFirst(sourceSpecification.getDocSpaceQualifiedPath(), ""));

				documentSpaceClient.copyObject(copyObjRequest);
				documentSpaceClient.deleteObject(new DeleteObjectRequest(bucketName, item.getKey()));
			}
		}
	}

	@Override
	public void archiveItem(UUID documentSpaceId, UUID parentFolderId, String name) {
		FilePathSpec filePathSpec = documentSpaceFileSystemService.getFilePathSpec(documentSpaceId, parentFolderId);
		documentSpaceFileSystemService.archiveElement(documentSpaceId, filePathSpec.getFullPathSpec(), name);
	}

	@Transactional
	@Override
	public void archiveItems(UUID documentSpaceId, String currentPath, List<String> items) {
		for (String item : items) {
			documentSpaceFileSystemService.archiveElement(documentSpaceId, currentPath, item);
		}
	}

	@Transactional
	@Override
	public void unArchiveItems(UUID documentSpaceId, List<String> items) {
		documentSpaceFileSystemService.unArchiveElements(documentSpaceId, items);
	}

	@Transactional(dontRollbackOn={RecordNotFoundException.class})
	@Override
	public void deleteFile(UUID documentSpaceId, String path, String file) throws RecordNotFoundException {
		getDocumentSpaceOrElseThrow(documentSpaceId);
		FilePathSpec filePathSpec = documentSpaceFileSystemService.parsePathToFilePathSpec(documentSpaceId, path);
		String prefix = getPathPrefix(documentSpaceId, path, filePathSpec);
		
		DocumentSpaceFileSystemEntry documentSpaceFile = documentSpaceFileService
				.getFileInDocumentSpaceFolder(documentSpaceId, filePathSpec.getItemId(), file).orElse(null);
		
		if (documentSpaceFile == null) {
			log.warn("Could not delete Document Space File: it does not exist in the database");
		} else {
			documentSpaceFileService.deleteDocumentSpaceFile(documentSpaceFile);
			documentSpaceFileSystemService.propagateModificationStateToAncestors(documentSpaceFile);
		}
		
		String fileKey = prefix + file;
		this.deleteS3ObjectByKey(fileKey);
	}
	
	@Transactional(dontRollbackOn={RecordNotFoundException.class})
	@Override
	public void deleteFile(UUID documentSpaceId, UUID parentFolderId, String filename) {
		FilePathSpec filePathSpec = documentSpaceFileSystemService.getFilePathSpec(documentSpaceId, parentFolderId);
		DocumentSpaceFileSystemEntry documentSpaceFile = documentSpaceFileService
				.getFileInDocumentSpaceFolder(documentSpaceId, filePathSpec.getItemId(), filename).orElse(null);
		
		if (documentSpaceFile == null) {
			log.warn("Could not delete Document Space File: it does not exist in the database");
		} else {
			documentSpaceFileService.deleteDocumentSpaceFile(documentSpaceFile);
			documentSpaceFileSystemService.propagateModificationStateToAncestors(documentSpaceFile);
		}
		
		this.deleteS3ObjectByKey(getPathPrefix(documentSpaceId, filePathSpec.getFullPathSpec(), filePathSpec) + filename);
	}

	@Override
	public void renameFolder(UUID documentSpaceId, String pathAndFolder, String newFolderName) {
		documentSpaceFileSystemService.renameFolder(documentSpaceId, pathAndFolder, newFolderName);
	}

	/**
	 * Provides ability to delete a file by its fully qualified S3 key path
	 * @param objKey
	 * @throws RecordNotFoundException
	 */
	public void deleteS3ObjectByKey(String objKey) throws RecordNotFoundException {
		try {
			documentSpaceClient.deleteObject(bucketName, objKey);
		} catch (AmazonServiceException ex) {
			if (ex.getStatusCode() == 404) {
				throw new RecordNotFoundException(String.format("File to delete: %s does not exist", objKey));
			}

			throw ex;
		}
	}
	
	/**
	 * Deletes a list of S3 objects by their key
	 * @param objKeys the array of object keys to delete
	 * @return a list of object keys of the items that failed to be deleted from S3 or an empty list if no errors
	 */
	@Override
	public List<DeleteError> deleteS3ObjectsByKey(String[] objKeys) {
		DeleteObjectsRequest deleteObjectsRequest = new DeleteObjectsRequest(bucketName)
				.withKeys(objKeys);
		
		try {
			documentSpaceClient.deleteObjects(deleteObjectsRequest);
		} catch (MultiObjectDeleteException ex) {
			return ex.getErrors();
		}
		
		return new ArrayList<>();
	}

	@Transactional
	@Override
	public FilePathSpec createFolder(UUID documentSpaceId, String path, String name) {
		return documentSpaceFileSystemService.convertFileSystemEntityToFilePathSpec(
				documentSpaceFileSystemService.addFolder(documentSpaceId, name, path));
	}

	@Override
	public FilePathSpecWithContents getFolderContents(UUID documentSpaceId, String path) {
		return documentSpaceFileSystemService.getFilesAndFoldersAtPath(documentSpaceId, path);
	}

	@Override
	public List<DocumentDto> getArchivedContents(UUID documentSpaceId) {
		return documentSpaceFileSystemService.getArchivedItems(documentSpaceId);
	}

	@Override
	public List<DocumentDto> getAllArchivedContentsForAuthUser(Principal principal) {
		List<DocumentSpaceResponseDto> accessibleSpaces = this.listSpaces(principal.getName());
		List<DocumentDto> archivedItemsList = new ArrayList<>();
		for (DocumentSpaceResponseDto space : accessibleSpaces) {
			List<DocumentDto> archivedItems = this.getArchivedContents(space.getId());
			for (DocumentDto dto : archivedItems) {

				// add space name to the return data
				dto.setSpaceName(space.getName());
			}
			archivedItemsList.addAll(archivedItems);
		}

		return archivedItemsList;
	}

	/**
	 * Lists ALL files in the identified space - starting at root of the space.
	 * @param documentSpaceId doc space ID
	 * @param continuationToken
	 * @param limit
	 * @return
	 * @throws RecordNotFoundException
	 */
	@Override
	public S3PaginationDto listFiles(UUID documentSpaceId, String continuationToken, @Nullable Integer limit)
			throws RecordNotFoundException {
		DocumentSpace documentSpace = getDocumentSpaceOrElseThrow(documentSpaceId);
		String prefix = this.createDocumentSpacePathPrefix(documentSpace.getId());

		if (limit == null) {
			limit = 20;
		}

		ListObjectsV2Request request = new ListObjectsV2Request().withBucketName(bucketName)
				.withPrefix(prefix).withMaxKeys(limit)
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

	/**
	 * Gets an elements info/status for a given path and file name
	 * @param documentSpaceId doc space Id
	 * @param path the path
	 * @param element the file name
	 * @return its FilePathSpec (if it existed)
	 */
	@Override
	public FilePathSpec statFileAtPath(UUID documentSpaceId, String path, String element) {
		return documentSpaceFileSystemService.parsePathToFilePathSpec(documentSpaceId,
				joinPathParts(path, element));
	}

	/**
	 * Gets a folders total size on disk
	 * @param documentSpaceId doc space id
	 * @param pathWithFolderName path including our folder name
	 * @return DocumentSpaceFolderInfoDto with item's info and its size in the size field
	 */
	@Override
	public DocumentSpaceFolderInfoDto getFolderSize(UUID documentSpaceId, String pathWithFolderName) {
		return documentSpaceFileSystemService.getFolderTotalSizeFromElement(documentSpaceFileSystemService.parsePathToFilePathSpec(documentSpaceId, pathWithFolderName));
	}

	@Override
	public void downloadAllInSpaceAndCompress(UUID documentSpaceId, OutputStream out) throws RecordNotFoundException {
		// dump all files and folders at this path and down
		FileSystemElementTree contentsAtPath = documentSpaceFileSystemService.dumpElementTree(documentSpaceId, DocumentSpaceFileSystemServiceImpl.PATH_SEP, false);
		// flatten the tree
		List<S3ObjectAndFilename> objects = documentSpaceFileSystemService.flattenTreeToS3ObjectAndFilenameList(contentsAtPath);
		writeZipFile(out, objects);
	}

	/**
	 * Private helper to write items to a zip file output stream
	 * @param out outstream
	 * @param objects list of S3ObjectAndFilename objects
	 */
	private void writeZipFile(OutputStream out, List<S3ObjectAndFilename> objects) {
		try (BufferedOutputStream bos = new BufferedOutputStream(out);
			 ZipOutputStream zipOut = new ZipOutputStream(bos)) {

			objects.forEach(item -> {
				// add item to zip - creating the expected folder structure as we do
				//  ensure zip folder entries do not have a leading slash since that creates warnings on
				//  unzip on some systems - signature of possible zip-slip exploit
				ZipEntry entry = new ZipEntry(item.getPathAndFileNameWithoutLeadingSlash());
				S3Object object = documentSpaceClient.getObject(bucketName, item.getS3Object().getKey());

				// workaround to get our zip entries to stay in UTC time, otherwise according to its javadoc (and confirmed it does)
				//  it will coerce given last modification date to the systems default... which SHOULD be UTC on the servers
				//  but here we make sure and it helps too on dev machines
				LocalDateTime ldt;
				try {
					ldt = new Date(Long.parseLong(object.getObjectMetadata()
							.getUserMetadata()
							.get("lastModified")))
							.toInstant()
							.atZone(ZoneOffset.UTC)
							.toLocalDateTime();
				} catch (Exception e) { //NOSONAR
					// catch any thing that goes wrong getting metadata (i.e. doesnt exist, format wrong, etc)
					ldt = object.getObjectMetadata().getLastModified().toInstant().atZone(ZoneOffset.UTC).toLocalDateTime();
				}

				entry.setTimeLocal(ldt);

				try (S3ObjectInputStream dataStream = object.getObjectContent()) {
					zipOut.putNextEntry(entry);
					dataStream.transferTo(zipOut);
					zipOut.closeEntry();
					object.close();
				} catch (IOException e) {
					log.warn("Failed to compress file: " + item.getPathAndFilename());
				}
			});
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
	public List<S3ObjectSummary> getAllFilesInFolder(UUID documentSpaceId, String prefix, boolean includeArchived) {
		List<S3ObjectSummary> files = new ArrayList<>();
		getDocumentSpaceOrElseThrow(documentSpaceId);
		FilePathSpec spec = documentSpaceFileSystemService.parsePathToFilePathSpec(documentSpaceId, prefix);
		ListObjectsV2Request request = new ListObjectsV2Request()
				.withBucketName(bucketName)
				.withPrefix(spec.getDocSpaceQualifiedPath())  // get the path in UUID form
				.withDelimiter(DocumentSpaceFileSystemServiceImpl.PATH_SEP);  // make sure we get only one-level deep

		ListObjectsV2Result objectListing = documentSpaceClient.listObjectsV2(request);
		boolean hasNext = true;

		do {
			if (objectListing.getNextContinuationToken() == null) {
				hasNext = false;
			}

			List<S3ObjectSummary> fileSummaries = objectListing.getObjectSummaries();

			// only include the S3 objects that our file system database says are not archived...
			for (S3ObjectSummary item : fileSummaries) {
				String fileName = FilenameUtils.getName(item.getKey());
				if (includeArchived || !documentSpaceFileSystemService.isArchived(documentSpaceId, spec.getItemId(), fileName)) {
					files.add(item);
				}
			}

			if (hasNext) {
				request = request.withContinuationToken(objectListing.getNextContinuationToken());
				objectListing = documentSpaceClient.listObjectsV2(request);
			}
		} while (hasNext);

		return files;
	}

	@Override
	public DocumentDto convertS3SummaryToDto(String documentSpacePathPrefix, UUID documentSpaceId, S3ObjectSummary objSummary) {
		return DocumentDto.builder().key(objSummary.getKey().replace(documentSpacePathPrefix, ""))
				.path(documentSpacePathPrefix).size(objSummary.getSize()).lastModifiedBy("")
				.spaceId(documentSpaceId.toString())
				.lastModifiedDate(objSummary.getLastModified()).build();
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

	@Override
	public DocumentSpace getDocumentSpaceOrElseThrow(UUID documentSpaceId) throws RecordNotFoundException {
		Optional<DocumentSpace> optionalDocumentSpace = documentSpaceRepository.findById(documentSpaceId);

		return optionalDocumentSpace.orElseThrow(
				() -> new RecordNotFoundException(String.format("Document Space with id: %s not found", documentSpaceId)));
	}

	protected String createDocumentSpacePathPrefix(UUID documentSpaceId) {
		return documentSpaceId.toString() + "/";
	}

	@Transactional
	@Override
	public void addDashboardUserToDocumentSpace(UUID documentSpaceId, DocumentSpaceDashboardMemberRequestDto documentSpaceDashboardMemberDto) throws RecordNotFoundException {
		DocumentSpace documentSpace = getDocumentSpaceOrElseThrow(documentSpaceId);

		List<DocumentSpacePrivilegeType> privilegeTypes = this.mapToPrivilegeTypes(documentSpaceDashboardMemberDto.getPrivileges());
		// Implicitly add READ privilege
		privilegeTypes.add(DocumentSpacePrivilegeType.READ);
		DashboardUser dashboardUser = documentSpacePrivilegeService.createDashboardUserWithPrivileges(documentSpaceDashboardMemberDto.getEmail(), documentSpace,
				privilegeTypes);

		documentSpace.addDashboardUser(dashboardUser);

		documentSpaceRepository.save(documentSpace);
	}

	private void batchAddDashboardUserToDocumentSpace(DocumentSpace documentSpace, Set<DocumentSpaceDashboardMemberRequestDto> documentSpaceDashboardMemberDto) {

		Set<DashboardUser> dashBoardUsers = documentSpaceDashboardMemberDto.stream().map(member -> {
				List<DocumentSpacePrivilegeType> privilegeTypes = this.mapToPrivilegeTypes(member.getPrivileges());
				// always add read permission
				privilegeTypes.add(DocumentSpacePrivilegeType.READ);
				return documentSpacePrivilegeService.createDashboardUserWithPrivileges(member.getEmail(), documentSpace,
						privilegeTypes);
				}
		).collect(Collectors.toSet());

		dashBoardUsers.forEach(documentSpace::addDashboardUser);

		documentSpaceRepository.save(documentSpace);
	}

	@Override
	public void removeDashboardUserFromDocumentSpace(UUID documentSpaceId, String[] emails) throws RecordNotFoundException {

		List<String> trimmedEmails = Arrays.stream(emails)
				.map(item -> item != null ? item.trim() : null)
				.collect(Collectors.toList());

		DocumentSpace documentSpace = getDocumentSpaceOrElseThrow(documentSpaceId);

		for (String email : trimmedEmails) {
			DashboardUser dashboardUser = dashboardUserService.getDashboardUserByEmailAsLower(email);

			if (dashboardUser == null) {
				continue;
			}

			documentSpacePrivilegeService.removePrivilegesFromDashboardUser(dashboardUser, documentSpace);

			documentSpace.removeDashboardUser(dashboardUser);

			Set<Privilege> privileges = dashboardUser.getPrivileges();

			if (dashboardUser.getDocumentSpaces().isEmpty()) {
				Optional<Privilege> documentSpaceGlobalPrivilege = privilegeRepository.findByName(DOCUMENT_SPACE_USER_PRIVILEGE);
				documentSpaceGlobalPrivilege.ifPresentOrElse(
						dashboardUser::removePrivilege,
						() -> log.error(String.format(
								"Could not remove Global Document Space Privilege (%s) from user because it is is missing",
								DOCUMENT_SPACE_USER_PRIVILEGE)));
			}

			if (privileges.size() == 1) {
				Optional<Privilege> first = privileges.stream().findFirst();
				if (first.isPresent() && first.get().getName().equals(DASHBOARD_USER_PRIV)) {
					dashboardUserService.deleteDashboardUser(dashboardUser.getId());
				}
			}
		}
		
		documentSpaceRepository.save(documentSpace);
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
		DashboardUser dashboardUser = getDashboardUserOrElseThrow(dashboardUserEmail);

		List<DocumentSpaceDashboardMemberPrivilegeRow> dashboardUserDocumentSpacePrivilegeRows =
				documentSpacePrivilegeService.getAllDashboardMemberPrivilegeRowsForDocumentSpace(documentSpace, Set.of(dashboardUser.getId()));

		List<DocumentSpacePrivilegeDto> dashboardUserDocumentSpacePrivileges = dashboardUserDocumentSpacePrivilegeRows.stream().map(row -> {
			DocumentSpacePrivilege privilege = row.getPrivilege();
			return new DocumentSpacePrivilegeDto(privilege.getId(), privilege.getType());
		}).collect(Collectors.toList());

		if (dashboardUserDocumentSpacePrivileges.isEmpty() && !dashboardUserService.dashboardUserIsAdmin(dashboardUser)) {
			// if we dont have privs and we're not an admin, throw access denied
			throw new NotAuthorizedException("Not Authorized to this Document Space");

		} else if (dashboardUserDocumentSpacePrivileges.isEmpty() && dashboardUserService.dashboardUserIsAdmin(dashboardUser)) {
			// if we have no privs, and if we're a dashboard admin, return all possible privs for this document space
			//  and just list the null UUID (all zeros) for the priv id, since there's no real persisted priv
			//  if we're not explicitly assigned to this space..
			return Arrays.stream(DocumentSpacePrivilegeType.values())
					.map(item -> new DocumentSpacePrivilegeDto(DocumentSpaceFileSystemEntry.NIL_UUID, item))
					.collect(Collectors.toList());
		}

		// else return the privs we got from the database
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
				if (i == 0) {
					validateCSVHeader(strings.get(i), errorList);
				} else if(strings.get(i).length != 0) {
					DocumentSpaceDashboardMemberRequestDto newDashboardSpaceMember = processCSVRow(strings.get(i), i, errorList);
					if (newDashboardSpaceMember.getEmail() == null) { // case where an error was found
						continue;
					} else if (membersInSpace.contains(newDashboardSpaceMember.getEmail())) { // case for email exists already
						errorList.add(String.format("Unable to add user with email %s, they are already a part of the space", newDashboardSpaceMember.getEmail()));
					} else if (membersToAdd.stream().anyMatch(m->m.getEmail().equals(newDashboardSpaceMember.getEmail()))) { // case where email was found in the csv previously
						errorList.add("Duplicate email found on row " + (i + 1));
					} else {
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

	private void validateCSVHeader(String[] row, List<String> errorList) {

		if (!row[0].trim().equalsIgnoreCase("email")) {
			errorList.add("Improper first CSV header: should be 'email'");
		} else if (!row[1].trim().equalsIgnoreCase("privilege")) {
			errorList.add("Improper second CSV header: should be 'privilege'");
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
		if (email.equals("")) {
			errorList.add("Missing email on row " + (i + 1));
			return memberToAdd;
		} else {
			memberToAdd.setEmail(email);
		}

		// three possible, case-insensitve values sent from frontend
		//  can be 'Viewer' (implicitly granted), 'Editor', 'Admin'
		//  If someone is ADMIN then they get MEMBERSHIP and WRITE (and of course READ for free later on)
		//  If someone is EDITOR then they get WRITE and READ
		//  If someone is VIEWER then we don't have to do anything here
		//  If we get an invalid privilege or blank, then we just let them know, but give the candidate READ (by virtue of doing nothing)
		if (row[1].trim().equalsIgnoreCase("ADMIN")) {
			memberToAdd.getPrivileges().add(ExternalDocumentSpacePrivilegeType.MEMBERSHIP);
			memberToAdd.getPrivileges().add(ExternalDocumentSpacePrivilegeType.WRITE);
		}
		else if (row[1].trim().equalsIgnoreCase("EDITOR")) {
			memberToAdd.getPrivileges().add(ExternalDocumentSpacePrivilegeType.WRITE);
		}
		else if (!row[1].trim().equalsIgnoreCase("VIEWER")) {
			errorList.add("Invalid permission on row " + (i + 1) + " - granted 'VIEWER'");
		}

		return memberToAdd;
	}

	@Override
	public void setDashboardUserDefaultDocumentSpace(UUID documentSpaceId, String dashboardUserEmail) {
		DocumentSpace documentSpace = getDocumentSpaceOrElseThrow(documentSpaceId);
		DashboardUser dashboardUser = getDashboardUserOrElseThrow(dashboardUserEmail);

		dashboardUser.setDefaultDocumentSpaceId(documentSpace.getId());

		dashboardUserRepository.save(dashboardUser);
	}

	private DashboardUser getDashboardUserOrElseThrow(String dashboardUserEmail) throws RecordNotFoundException {
		DashboardUser dashboardUser = dashboardUserService.getDashboardUserByEmailAsLower(dashboardUserEmail);

		if (dashboardUser == null) {
			throw new RecordNotFoundException("Requesting Document Space Dashboard User does not exist with email: " + dashboardUserEmail);
		}
		return dashboardUser;
	}

	@Override
	public void unsetDashboardUsersDefaultDocumentSpace(DocumentSpace documentSpace) {
		dashboardUserRepository.unsetDashboardUsersDefaultDocumentSpaceForDocumentSpace(documentSpace.getId());
	}

	@Override
	public Page<RecentDocumentDto> getRecentlyUploadedFilesByAuthUser(String authenticatedUsername,
			Pageable pageable) {
		List<DocumentSpaceResponseDto> authorizedSpaces = listSpaces(authenticatedUsername);
		Set<UUID> authorizedSpaceIds = authorizedSpaces.stream().map(DocumentSpaceResponseDto::getId).collect(Collectors.toSet());
		Page<RecentDocumentDto> results = documentSpaceFileService.getRecentlyUploadedFilesByUser(authenticatedUsername, authorizedSpaceIds, pageable);

		// now map the results to populate its string path
		results.forEach(item -> {
			FilePathSpec spec = documentSpaceFileSystemService.getFilePathSpec(item.getDocumentSpace().getId(), item.getParentFolderId());
			String path = FilenameUtils.normalizeNoEndSeparator(spec.getFullPathSpec());
			item.setPath(path.isBlank() ? "/" : path);
		});

		return results;
	}

	@Override
	public Page<RecentDocumentDto> getRecentlyUploadedFilesBySpace(UUID spaceId, Date date, Pageable pageable) {
		Page<RecentDocumentDto> results =  documentSpaceFileService.getRecentlyUploadedFilesBySpace(spaceId, date, pageable);

		// now map the results to populate its string path
		results.forEach(item -> {
				FilePathSpec spec = documentSpaceFileSystemService.getFilePathSpec(item.getDocumentSpace().getId(), item.getParentFolderId());
				String path = FilenameUtils.normalizeNoEndSeparator(spec.getFullPathSpec());
				item.setPath(path.isBlank() ? "/" : path);
		});

		return results;
	}

	private List<DocumentSpacePrivilegeType> mapToPrivilegeTypes(List<ExternalDocumentSpacePrivilegeType> privileges) {
		if (privileges == null) {
			return new ArrayList<>();
		}
		return privileges.stream().map(privilege -> {
			DocumentSpacePrivilegeType mappedType;
			switch (privilege) {
				case WRITE:
					mappedType = DocumentSpacePrivilegeType.WRITE;
					break;
				case MEMBERSHIP:
					mappedType = DocumentSpacePrivilegeType.MEMBERSHIP;
					break;
				default:
					throw new IllegalArgumentException("Membership privilege provided is not supported.");
			}
			return mappedType;
		}).collect(Collectors.toList());
	}

	/**
	 * Perform the filename search query over the document space
	 * @param spaceId space UUID
	 * @param filename the filename query
	 * @param pageable the pagination information passed in from the controller
	 * @param principal the user principal id
	 * @return a pageable of DocumentMobileDto to return
	 */
	@Override
	public Page<DocumentMobileDto> findFilesInSpaceLike(UUID spaceId, String filename, Pageable pageable, Principal principal) {
		Page<DocumentSpaceFileSystemEntry> results = documentSpaceFileSystemService.findFilesInSpaceLike(spaceId, filename, pageable);
		List<DocumentSpaceUserCollectionResponseDto> favs = documentSpaceUserCollectionService.getFavoriteEntriesForUserInDocumentSpace(principal.getName(), spaceId);
		return new PageImpl<>(results.stream()
				.map(item -> convertFileSystemEntryToMobileDto(spaceId, item, favs, principal))
				.collect(Collectors.toList()), pageable, results.getSize());
	}

	/**
	 * Helper method to map a DocumentSpaceFileSystemEntry over to a DocumentMobileDto (same as a DocumentDto but with extras included)
	 * @param spaceId the space UUID
	 * @param entry the doc space entry to convert
	 * @param principal the principal id (so we can get favorites)
	 * @return the document mobile dto
	 */
	@Override
	public DocumentMobileDto convertFileSystemEntryToMobileDto(UUID spaceId, DocumentSpaceFileSystemEntry entry,
															   List<DocumentSpaceUserCollectionResponseDto> favs,
															   Principal principal) {
		FilePathSpec spec = documentSpaceFileSystemService.getFilePathSpec(spaceId, entry.getParentEntryId());

		return DocumentMobileDto.builder()
				.path(FilenameUtils.normalizeNoEndSeparator(spec.getFullPathSpec()))
				.size(entry.getSize())
				.spaceId(entry.getDocumentSpaceId().toString())
				.isFolder(entry.isFolder())
				.parentId(entry.getParentEntryId())
				.isFavorite(favs.stream().anyMatch(item -> item.getItemId().equals(entry.getItemId())))
				.elementUniqueId(entry.getItemId())
				.key(FilenameUtils.getName(entry.getItemName()))
				.lastModifiedBy(entry.getLastModifiedBy() != null ? entry.getLastModifiedBy() : entry.getCreatedBy())
				.lastModifiedDate(entry.getLastModifiedOn() != null ? entry.getLastModifiedOn() : entry.getCreatedOn())
				.lastActivity(entry.getLastActivity() != null ? entry.getLastActivity() : entry.getCreatedOn())
				.hasContents(documentSpaceFileSystemService.checkIfItemHasContents(entry.getItemId()))
				.build();
	}
}
