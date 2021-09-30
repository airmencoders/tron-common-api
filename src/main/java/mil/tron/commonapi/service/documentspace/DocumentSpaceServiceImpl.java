package mil.tron.commonapi.service.documentspace;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.s3.model.DeleteObjectsRequest.KeyVersion;
import com.amazonaws.services.s3.transfer.Upload;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceInfoDto;
import mil.tron.commonapi.dto.documentspace.S3PaginationDto;
import mil.tron.commonapi.entity.documentspace.DocumentSpace;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.transfer.TransferManager;

import lombok.extern.slf4j.Slf4j;
import mil.tron.commonapi.dto.documentspace.DocumentDetailsDto;
import mil.tron.commonapi.dto.documentspace.DocumentDto;
import mil.tron.commonapi.exception.BadRequestException;

@Slf4j
@Service
@ConditionalOnProperty(value = "minio.enabled", havingValue = "true")
public class DocumentSpaceServiceImpl implements DocumentSpaceService {
	private final AmazonS3 documentSpaceClient;
	private final TransferManager documentSpaceTransferManager;
	private final String bucketName;
	private final DocumentSpaceRepository documentSpaceRepository;

	public DocumentSpaceServiceImpl(
			AmazonS3 documentSpaceClient, 
			TransferManager documentSpaceTransferManager,
			@Value("${minio.bucket-name}") String bucketName,
			DocumentSpaceRepository documentSpaceRepository) {
		this.documentSpaceClient = documentSpaceClient;
		this.bucketName = bucketName;
		this.documentSpaceTransferManager = documentSpaceTransferManager;
		this.documentSpaceRepository = documentSpaceRepository;
	}

	@Override
	public List<DocumentSpaceInfoDto> listSpaces() {
		return documentSpaceRepository.findAllDynamicBy(DocumentSpaceInfoDto.class);
	}

	@Override
	public DocumentSpaceInfoDto createSpace(DocumentSpaceInfoDto dto) {
		DocumentSpace documentSpace = convertDocumentSpaceDtoToEntity(dto);
		documentSpace.setId(UUID.randomUUID());
		
		return convertDocumentSpaceEntityToDto(documentSpaceRepository.save(documentSpace));
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
    	
		return fileKeys.stream()
				.map(item -> documentSpaceClient.getObject(bucketName, createDocumentSpacePathPrefix(documentSpace.getId()) + item))
				.collect(Collectors.toList());
	}

	@Override
	public void downloadAndWriteCompressedFiles(UUID documentSpaceId, Set<String> fileKeys, OutputStream out) throws RecordNotFoundException {
		List<S3Object> files = getFiles(documentSpaceId, fileKeys);
		
    	try (BufferedOutputStream bos = new BufferedOutputStream(out);
    	    	ZipOutputStream zipOut = new ZipOutputStream(bos);) {
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
					createDocumentSpacePathPrefix(documentSpace.getId()) + file.getOriginalFilename(),
					file.getInputStream(), metaData);
			
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

	/**
	 * List the documents in a space
	 * @param spaceName
	 * @return
	 */
	@Override
    public S3PaginationDto listFiles(UUID documentSpaceId, String continuationToken, Integer limit) throws RecordNotFoundException {
		DocumentSpace documentSpace = getDocumentSpaceOrElseThrow(documentSpaceId);
		
		if (limit == null) {
			limit = 20;
		}
		
		ListObjectsV2Request request = new ListObjectsV2Request()
				.withBucketName(bucketName)
				.withPrefix(createDocumentSpacePathPrefix(documentSpace.getId()))
				.withMaxKeys(limit)
				.withContinuationToken(continuationToken);
		
		ListObjectsV2Result objectListing = documentSpaceClient.listObjectsV2(request);
		List<S3ObjectSummary> summary = objectListing.getObjectSummaries();
		
		List<DocumentDto> documents = summary
				.stream()
        		.map(item -> this.convertS3SummaryToDto(createDocumentSpacePathPrefix(documentSpace.getId()), item))
				.collect(Collectors.toList());

    	return S3PaginationDto.builder()
    			.currentContinuationToken(objectListing.getContinuationToken())
    			.nextContinuationToken(objectListing.getNextContinuationToken())
    			.documents(documents)
    			.size(limit)
    			.totalElements(documents.size())
    			.build();
    }
	
	@Override
	public void downloadAllInSpaceAndCompress(UUID documentSpaceId, OutputStream out) throws RecordNotFoundException {
		DocumentSpace documentSpace = getDocumentSpaceOrElseThrow(documentSpaceId);
		
		try (BufferedOutputStream bos = new BufferedOutputStream(out);
    	    	ZipOutputStream zipOut = new ZipOutputStream(bos);) {
			ListObjectsV2Request request = new ListObjectsV2Request()
					.withBucketName(bucketName)
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
			} while(hasNext);
    		
    		
    		zipOut.finish();
    	} catch (IOException e1) {
			log.warn("Failure occurred closing zip output stream");
		}
	}

	@Override
	public DocumentDto convertS3ObjectToDto(S3Object s3Object) {
		return DocumentDto.builder()
				.key(s3Object.getKey())
				.path("")
				.size(s3Object.getObjectMetadata().getContentLength())
				.uploadedBy("")
				.uploadedDate(s3Object.getObjectMetadata().getLastModified())
				.build();
	}
	
	@Override
	public DocumentDto convertS3SummaryToDto(String documentSpacePathPrefix, S3ObjectSummary objSummary) {
		return DocumentDto.builder()
				.key(objSummary.getKey().replace(documentSpacePathPrefix, ""))
				.path(documentSpacePathPrefix)
				.size(objSummary.getSize())
				.uploadedBy("")
				.uploadedDate(objSummary.getLastModified())
				.build();
	}

	@Override
	public DocumentDetailsDto convertToDetailsDto(S3Object s3Object) {
		return DocumentDetailsDto.builder()
				.key(s3Object.getKey())
				.path("")
				.size(s3Object.getObjectMetadata().getContentLength())
				.uploadedBy("")
				.uploadedDate(s3Object.getObjectMetadata().getLastModified())
				.metadata(s3Object.getObjectMetadata())
				.build();
	}
	
	@Override
	public DocumentSpace convertDocumentSpaceDtoToEntity(DocumentSpaceInfoDto documentSpaceInfoDto) {
		return DocumentSpace.builder()
				.id(documentSpaceInfoDto.getId())
				.name(documentSpaceInfoDto.getName())
				.build();
	}
	
	@Override
	public DocumentSpaceInfoDto convertDocumentSpaceEntityToDto(DocumentSpace documentSpace) {
		return DocumentSpaceInfoDto.builder()
				.id(documentSpace.getId())
				.name(documentSpace.getName())
				.build();
	}
	
	private DocumentSpace getDocumentSpaceOrElseThrow(UUID documentSpaceId) throws RecordNotFoundException {
		Optional<DocumentSpace> optionalDocumentSpace = documentSpaceRepository.findById(documentSpaceId);

		return optionalDocumentSpace.orElseThrow(() -> new RecordNotFoundException(
				String.format("Document Space with id: %s not found", documentSpaceId)));
	}
	
	protected String createDocumentSpacePathPrefix(UUID documentSpaceId) {
		return documentSpaceId.toString() + "/";
	}
	
	/**
	 * Writes an S3 Object to the output stream. This will close the
	 * input stream of the S3 Object.
	 * 
	 * @param zipOutputStream output stream
	 * @param s3Object {@link S3Object} to write to output stream
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
}
