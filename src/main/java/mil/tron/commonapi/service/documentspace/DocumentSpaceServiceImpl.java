package mil.tron.commonapi.service.documentspace;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
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
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;

import lombok.extern.slf4j.Slf4j;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.exception.FileCompressionException;

@Slf4j
@Service
@ConditionalOnProperty(value = "minio.enabled", havingValue = "true")
public class DocumentSpaceServiceImpl implements DocumentSpaceService {
	private final AmazonS3 documentSpaceClient;
	private final TransferManager transferManager;
	
	@Value("${minio.bucket-name}")
    private String bucketName;
	
	public DocumentSpaceServiceImpl(AmazonS3 documentSpaceClient) {
		this.documentSpaceClient = documentSpaceClient;
		
		this.transferManager = TransferManagerBuilder.standard().withS3Client(documentSpaceClient).build();
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
    public S3Object downloadFile(String fileKey) {
		return getFile(fileKey);
    }

    @Override
    public void deleteFile(String fileKey) {
        documentSpaceClient.deleteObject(bucketName, fileKey);
    }

    @Override
    public List<String> listFiles() {
        List<String> list = new LinkedList<>();
        documentSpaceClient.listObjects(bucketName).getObjectSummaries().forEach(itemResult -> list.add(itemResult.getKey()));
        return list;
    }

    @Override
    public S3Object getFile(String key) {
		return documentSpaceClient.getObject(bucketName, key);
    }

	@Override
	public void downloadAndWriteCompressedFiles(String[] fileKeys, OutputStream out) {
		List<S3Object> files = getFiles(fileKeys);
		
		BufferedOutputStream bos = new BufferedOutputStream(out);
    	ZipOutputStream zipOut = new ZipOutputStream(bos);
    	
    	files.forEach(item -> {
    		ZipEntry entry = new ZipEntry(item.getKey());
    		
    		try {
				zipOut.putNextEntry(entry);
				
				var dataStream = item.getObjectContent();
				
				byte[] buf = new byte[2048];
			    int len;
			    while ((len = dataStream.read(buf, 0, 2048)) > 0) {
			    	zipOut.write(buf, 0, len);
			    }
				
			    zipOut.closeEntry();
				dataStream.close();
			} catch (IOException e) {
				throw new FileCompressionException("Failed to compress files. Failed on: " + item.getKey());
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
	public List<S3Object> getFiles(String[] fileKeys) {
		List<String> urls = Arrays.asList(fileKeys);
		return urls.stream().map(this::getFile).collect(Collectors.toList());
	}

}
