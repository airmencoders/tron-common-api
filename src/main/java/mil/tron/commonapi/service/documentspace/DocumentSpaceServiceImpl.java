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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
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
	
	public DocumentSpaceServiceImpl(AmazonS3 documentSpaceClient, @Value("${minio.bucket-name}") String bucketName) {
		this.documentSpaceClient = documentSpaceClient;
		this.bucketName = bucketName;
		
		this.transferManager = TransferManagerBuilder.standard().withS3Client(documentSpaceClient).build();
	}

    @Override
    public S3Object getFile(String key) {
		return documentSpaceClient.getObject(bucketName, key);
    }
    
    @Override
    public S3Object downloadFile(String fileKey) {
		return getFile(fileKey);
    }
    
    @Override
	public List<S3Object> getFiles(Set<String> fileKeys) {
		return fileKeys.stream().map(this::getFile).collect(Collectors.toList());
	}

	@Override
	public void downloadAndWriteCompressedFiles(Set<String> fileKeys, OutputStream out) {
		List<S3Object> files = getFiles(fileKeys);
		
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
    public void uploadFile(MultipartFile file) {
		ObjectMetadata metaData = new ObjectMetadata();
		metaData.setContentType(file.getContentType());
		metaData.setContentLength(file.getSize());
		
		try {
			transferManager.upload(bucketName, file.getOriginalFilename(), file.getInputStream(), metaData);
//			documentSpaceClient.putObject(bucketName, file.getOriginalFilename(), file.getInputStream(), metaData);
		} catch (IOException e) {
			throw new BadRequestException("Failed retrieving input stream");
		}
    }

    @Override
    public void deleteFile(String fileKey) {
        documentSpaceClient.deleteObject(bucketName, fileKey);
    }

    @Override
    public List<DocumentDto> listFiles() {
    	return documentSpaceClient.listObjects(bucketName).getObjectSummaries().stream()
        		.map(this::convertS3SummaryToDto).collect(Collectors.toList());
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
	public DocumentDto convertS3SummaryToDto(S3ObjectSummary objSummary) {
		return DocumentDto.builder()
				.key(objSummary.getKey())
				.path("")
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
