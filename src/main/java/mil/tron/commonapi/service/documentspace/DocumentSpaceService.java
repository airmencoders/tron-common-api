package mil.tron.commonapi.service.documentspace;

import java.io.OutputStream;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.model.S3Object;


public interface DocumentSpaceService {
	void uploadFile(MultipartFile file);
	S3Object downloadFile(String fileKey);
	void downloadAndWriteCompressedFiles(String[] fileKeys, OutputStream out);
	List<S3Object> getFiles(String[] fileKeys);
    void deleteFile(String fileKey);
    List<String> listFiles();
    S3Object getFile(String key);
}
