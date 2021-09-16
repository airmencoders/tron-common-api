package mil.tron.commonapi.service.documentspace;

import java.io.OutputStream;
import java.util.List;
import java.util.Set;

import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import mil.tron.commonapi.dto.documentspace.DocumentDetailsDto;
import mil.tron.commonapi.dto.documentspace.DocumentDto;

public interface DocumentSpaceService {
	S3Object getFile(String key);
	S3Object downloadFile(String fileKey);
	List<S3Object> getFiles(Set<String> fileKeys);
	void downloadAndWriteCompressedFiles(Set<String> fileKeys, OutputStream out);
	void uploadFile(MultipartFile file);
    void deleteFile(String fileKey);
    List<DocumentDto> listFiles();
    
    DocumentDto convertS3ObjectToDto(S3Object objSummary);
    DocumentDto convertS3SummaryToDto(S3ObjectSummary objSummary);
    DocumentDetailsDto convertToDetailsDto(S3Object objSummary);
}
