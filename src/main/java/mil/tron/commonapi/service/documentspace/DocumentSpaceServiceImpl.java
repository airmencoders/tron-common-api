package mil.tron.commonapi.service.documentspace;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.amazonaws.services.s3.internal.BucketNameUtils;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.s3.transfer.Upload;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceInfoDto;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import org.assertj.core.util.Lists;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;

import lombok.extern.slf4j.Slf4j;
import mil.tron.commonapi.dto.documentspace.DocumentDetailsDto;
import mil.tron.commonapi.dto.documentspace.DocumentDto;
import mil.tron.commonapi.exception.BadRequestException;

@Slf4j
@Service
@ConditionalOnProperty(value = "minio.enabled", havingValue = "true")
public class DocumentSpaceServiceImpl implements DocumentSpaceService {
	private final AmazonS3 documentSpaceClient;
	private final String bucketName;
	
	private final TransferManager transferManager;
	private static final String RESERVED_METAFILE = ".metafile";
	private static final String NAME_NULL_ERROR = "Space Name cannot be null";

	public DocumentSpaceServiceImpl(AmazonS3 documentSpaceClient, @Value("${minio.bucket-name}") String bucketName) {
		this.documentSpaceClient = documentSpaceClient;
		this.bucketName = bucketName;
		
		this.transferManager = TransferManagerBuilder.standard().withS3Client(documentSpaceClient).build();
	}

	/**
	 * Helper to grab first part of an s3 object's path be it a folder or filename itself
	 * Scans the path until end or up to and including the first '/'
	 * @param path
	 * @return
	 */
	private String grabToFirstSlash(String path) {
		char[] pathChars = path.toCharArray();
		StringBuilder result = new StringBuilder();
		for (char pathChar : pathChars) {
			result.append(pathChar);
			if (pathChar == '/') break;
		}
		return result.toString();
	}

	/**
	 * Helper to get files and folders at a specific level (aka prefix) in the bucket
	 * @param prefix
	 * @return
	 */
	private List<String> getObjects(String prefix) {
		String keyPrefix = prefix;

		ListObjectsRequest req = new ListObjectsRequest();
		req.setBucketName(bucketName);
		if (keyPrefix != null) {
			// make sure we end with '/'
			if (!keyPrefix.endsWith("/")) keyPrefix += "/";
			req.setPrefix(keyPrefix);
		}

		String finalKeyPrefix = keyPrefix == null ? "" : keyPrefix;
		return Lists.newArrayList(documentSpaceClient.listObjects(req)
				.getObjectSummaries()
				.stream()
				.map(item -> item.getKey().replace(finalKeyPrefix, "")) // get name and blank out prefix
				.map(this::grabToFirstSlash)    // get up to first slash or end of string
				.collect(Collectors.toSet()));  // de-dupe

	}

	/**
	 * See if a space name is valid (does not throw)
	 * @param name
	 * @return true if valid else false
	 */
	public static boolean verifySpaceNameValid(String name) {
		return BucketNameUtils.isValidV2BucketName(name) && !name.contains("/");
	}

	/**
	 * see if a space doesn't exist else throws
	 * @param name
	 * @throws ResourceAlreadyExistsException
	 */
	private void checkSpaceNotExists(String name) {
		if (name == null)
			throw new BadRequestException(NAME_NULL_ERROR);

		this.listSpaces().forEach(item -> {
			if (item.replace("/", "").equalsIgnoreCase(name)) {
				throw new ResourceAlreadyExistsException(String.format("Space with name %s already exists", name));
			}
		});
	}

	/**
	 * See if a space exists by name else throws
	 * @param name
	 * @throws RecordNotFoundException
	 */
	private void checkSpaceExists(String name) {
		if (name == null)
			throw new BadRequestException(NAME_NULL_ERROR);

		for (String s : this.listSpaces()) {
			if (s.replace("/", "")
					.equalsIgnoreCase(name.replace("/", ""))) {
				return;
			}
		}

		throw new RecordNotFoundException(String.format("Space with name %s not found", name));
	}

	/**
	 * See if a space name is valid (uses s3 bucket naming rules) or throws
	 * @param name
	 * @throws BadRequestException
	 */
	private void checkSpaceNameValid(String name) {
		if (name == null)
			throw new BadRequestException(NAME_NULL_ERROR);

		if (verifySpaceNameValid(name)) return;
		throw new BadRequestException("Invalid space name");
	}

	private void checkFileExists(String spaceName, String fileName) {
		if (!documentSpaceClient.doesObjectExist(bucketName, spaceName + "/" + fileName)) {
			throw new RecordNotFoundException(String.format("Cannot find file with name %s", fileName));
		}
	}

	/**
	 * Get the list of files and spaces and the root of our bucket
	 * @return list of string space names
	 */
	@Override
	public List<String> listSpaces() {
		return getObjects(null);
	}

	/**
	 * Creates a space (folder) within the bucket.  We can't create am empty folder though
	 * since folders are really just prefixes - so put in the hidden metafile that starts with a
	 * "."
	 *
	 * We wont allow dots to begin any filename elsewhere (since we use bucket name validation for
	 * all filenames and path compos)
	 * @param dto
	 * @return
	 */
	@Override
	public DocumentSpaceInfoDto createSpace(DocumentSpaceInfoDto dto) {
		checkSpaceNotExists(dto.getName());
		checkSpaceNameValid(dto.getName());
		this.documentSpaceClient.putObject(this.bucketName, dto.getName() + "/" + RESERVED_METAFILE, "folder_data");
		return dto;
	}

	/**
	 * Deletes a space (object) and all its descendants
	 * @param name name of the space
	 */
	@Override
	public void deleteSpace(String name) {
		checkSpaceExists(name);

		// get all objects in this space (prefix), and delete them
		//  effectively deleting the entire space itself
		List<String> keys = this.getObjects(name);
		keys.forEach(item -> this.documentSpaceClient.deleteObject(this.bucketName, name + "/" + item));
	}

    @Override
    public S3Object getFile(String space, String key) {
		checkSpaceExists(space);
		return documentSpaceClient.getObject(bucketName, space + "/" + key);
    }
    
    @Override
    public S3Object downloadFile(String space, String fileKey) {
		checkSpaceExists(space);
		return getFile(space, fileKey);
    }
    
    @Override
	public List<S3Object> getFiles(String space, Set<String> fileKeys) {
		checkSpaceExists(space);
		return fileKeys.stream()
				.filter(item -> !item.contains(RESERVED_METAFILE))
				.map(item -> documentSpaceClient.getObject(bucketName, space + "/" + item))
				.collect(Collectors.toList());
	}

	@Override
	public void downloadAndWriteCompressedFiles(String space, Set<String> fileKeys, OutputStream out) {
		checkSpaceExists(space);
		List<S3Object> files = getFiles(space, fileKeys);
		
		BufferedOutputStream bos = new BufferedOutputStream(out);
    	ZipOutputStream zipOut = new ZipOutputStream(bos);
    	
    	files.forEach(item -> {
    		ZipEntry entry = new ZipEntry(item.getKey());
    		
    		try {
				zipOut.putNextEntry(entry);
				
				var dataStream = item.getObjectContent();
				
				dataStream.transferTo(zipOut);
				
			    zipOut.closeEntry();
				dataStream.close();
			} catch (IOException e) {
				log.warn("Failed to compress file: " + item.getKey());
			}
    	});
    	
    	try {
			zipOut.finish();
			zipOut.close();
			
			bos.close();
		} catch (IOException e) {
			log.warn("Failed to close compression streams");
		}
    	
	}

	@Override
    public void uploadFile(String space, MultipartFile file) {
		checkSpaceExists(space);

		ObjectMetadata metaData = new ObjectMetadata();
		metaData.setContentType(file.getContentType());
		metaData.setContentLength(file.getSize());
		
		try {
			Upload upload = transferManager.upload(bucketName, space + "/" + file.getOriginalFilename(), file.getInputStream(), metaData);
			upload.waitForCompletion();
		} catch (IOException | InterruptedException e) {  //NOSONAR
			throw new BadRequestException("Failed retrieving input stream");
		}
    }

    @Override
    public void deleteFile(String space, String fileKey) {
		checkSpaceExists(space);
		checkFileExists(space, fileKey);
        documentSpaceClient.deleteObject(bucketName, space + "/" + fileKey);
    }

	/**
	 * List the documents in a space
	 * @param spaceName
	 * @return
	 */
	@Override
    public List<DocumentDto> listFiles(String spaceName) {
		checkSpaceExists(spaceName);

    	return documentSpaceClient
				.listObjects(bucketName, spaceName)
				.getObjectSummaries()
				.stream()
				.filter(item -> !item.getKey().contains(RESERVED_METAFILE))  // hide the metafile
        		.map(item -> this.convertS3SummaryToDto(spaceName, item))
				.collect(Collectors.toList());
    }

	@Override
	public DocumentDto convertS3ObjectToDto(S3Object s3Object) {
		return DocumentDto.builder()
				.key(s3Object.getKey())
				.path("")
				.uploadedBy("")
				.uploadedDate(new Date())
				.build();
	}
	
	@Override
	public DocumentDto convertS3SummaryToDto(String spaceName, S3ObjectSummary objSummary) {
		return DocumentDto.builder()
				.key(objSummary.getKey().replace(spaceName + "/", ""))
				.path(spaceName)
				.uploadedBy("")
				.uploadedDate(new Date())
				.build();
	}

	@Override
	public DocumentDetailsDto convertToDetailsDto(S3Object s3Object) {
		return DocumentDetailsDto.builder()
				.key(s3Object.getKey())
				.path("")
				.uploadedBy("")
				.uploadedDate(new Date())
				.metadata(s3Object.getObjectMetadata())
				.build();
	}
}
