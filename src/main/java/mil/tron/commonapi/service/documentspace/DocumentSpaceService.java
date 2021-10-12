package mil.tron.commonapi.service.documentspace;

import java.io.OutputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import mil.tron.commonapi.dto.documentspace.DocumentSpaceRequestDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceDashboardMemberRequestDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceDashboardMemberResponseDto;
import mil.tron.commonapi.dto.documentspace.S3PaginationDto;
import mil.tron.commonapi.entity.documentspace.DocumentSpace;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import mil.tron.commonapi.dto.documentspace.DocumentDetailsDto;
import mil.tron.commonapi.dto.documentspace.DocumentDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceResponseDto;

public interface DocumentSpaceService {
    List<DocumentSpaceResponseDto> listSpaces();
    DocumentSpaceResponseDto createSpace(DocumentSpaceRequestDto dto);
    
    /**
     * Deletes a space from database and all files associated with it in the bucket
     * @param documentSpaceId the ID of the document space to delete
     */
    void deleteSpace(UUID documentSpaceId);

	S3Object getFile(UUID documentSpaceId, String path, String key);
	S3Object downloadFile(UUID documentSpaceId, String path, String fileKey);
	void downloadAllInSpaceAndCompress(UUID documentSpaceId, OutputStream out);
	List<S3Object> getFiles(UUID documentSpaceId, String path, Set<String> fileKeys);
	void downloadAndWriteCompressedFiles(UUID documentSpaceId, String path, Set<String> fileKeys, OutputStream out);
	void uploadFile(UUID documentSpaceId, String path, MultipartFile file);
    void deleteFile(UUID documentSpaceId, String path, String fileKey);
    S3PaginationDto listFiles(UUID documentSpaceId, String path, String continuationToken, Integer limit);
    List<S3Object> getAllFilesInFolder(UUID documentSpaceId, String prefix);
    List<String> getAllFilesInFolderSummaries(UUID documentSpaceId, String prefix);
    FilePathSpec createFolder(UUID documentSpaceId, String path, String name);
    void deleteFolder(UUID documentSpaceId, String path);
    FilePathSpec getFolderContents(UUID documentSpaceId, String path);
    
    DocumentDto convertS3ObjectToDto(S3Object objSummary);
    DocumentDto convertS3SummaryToDto(String spaceName, S3ObjectSummary objSummary);
    DocumentDetailsDto convertToDetailsDto(S3Object objSummary);
    
    DocumentSpace convertDocumentSpaceRequestDtoToEntity(DocumentSpaceRequestDto documentSpaceInfoDto);
    DocumentSpaceResponseDto convertDocumentSpaceEntityToResponseDto(DocumentSpace documentSpace);
    
    void addDashboardUserToDocumentSpace(UUID documentSpaceId, DocumentSpaceDashboardMemberRequestDto documentSpaceMemberDto);

    Page<DocumentSpaceDashboardMemberResponseDto> getDashboardUsersForDocumentSpace(UUID documentSpaceId, Pageable pageable);

    void removeDashboardUserFromDocumentSpace(UUID documentSpaceId, String email);
}
