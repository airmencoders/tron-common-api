package mil.tron.commonapi.service.documentspace;

import java.io.OutputStream;
import java.util.List;
import java.util.Set;

import mil.tron.commonapi.dto.documentspace.DocumentSpaceInfoDto;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import mil.tron.commonapi.dto.documentspace.DocumentDetailsDto;
import mil.tron.commonapi.dto.documentspace.DocumentDto;

public interface DocumentSpaceService {
    List<String> listSpaces();
    DocumentSpaceInfoDto createSpace(DocumentSpaceInfoDto dto);
    void deleteSpace(String name);

	S3Object getFile(String space, String key);
	S3Object downloadFile(String space, String fileKey);
	List<S3Object> getFiles(String space, Set<String> fileKeys);
	void downloadAndWriteCompressedFiles(String space, Set<String> fileKeys, OutputStream out);
	void uploadFile(String space, MultipartFile file);
    void deleteFile(String space, String fileKey);
    List<DocumentDto> listFiles(String spaceName);
    
    DocumentDto convertS3ObjectToDto(S3Object objSummary);
    DocumentDto convertS3SummaryToDto(String spaceName, S3ObjectSummary objSummary);
    DocumentDetailsDto convertToDetailsDto(S3Object objSummary);
}
