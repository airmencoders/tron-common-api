package mil.tron.commonapi.service.documentspace;

import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.model.MultiObjectDeleteException.DeleteError;

import mil.tron.commonapi.dto.documentspace.*;
import mil.tron.commonapi.entity.documentspace.DocumentSpace;
import mil.tron.commonapi.service.documentspace.util.FilePathSpec;
import mil.tron.commonapi.service.documentspace.util.FilePathSpecWithContents;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface DocumentSpaceService {
    List<DocumentSpaceResponseDto> listSpaces(String username);
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
    List<String> deleteItems(UUID documentSpaceId, String currentPath, List<String> items);
    void renameFolder(UUID documentSpaceId, String pathAndFolder, String newFolderName);
    void deleteS3ObjectByKey(String objKey);
    List<DeleteError> deleteS3ObjectsByKey(String[] objKeys);
    S3PaginationDto listFiles(UUID documentSpaceId, String continuationToken, Integer limit);
    List<S3ObjectSummary> getAllFilesInFolder(UUID documentSpaceId, String prefix);
    FilePathSpec createFolder(UUID documentSpaceId, String path, String name);
    void deleteFolder(UUID documentSpaceId, String path);
    FilePathSpecWithContents getFolderContents(UUID documentSpaceId, String path);
    
    DocumentDto convertS3SummaryToDto(String spaceName, UUID documentSpaceId, S3ObjectSummary objSummary);

    DocumentSpace convertDocumentSpaceRequestDtoToEntity(DocumentSpaceRequestDto documentSpaceInfoDto);
    DocumentSpaceResponseDto convertDocumentSpaceEntityToResponseDto(DocumentSpace documentSpace);
    
    void addDashboardUserToDocumentSpace(UUID documentSpaceId, DocumentSpaceDashboardMemberRequestDto documentSpaceMemberDto);

    Page<DocumentSpaceDashboardMemberResponseDto> getDashboardUsersForDocumentSpace(UUID documentSpaceId, Pageable pageable);

    List<DocumentSpacePrivilegeDto> getDashboardUserPrivilegesForDocumentSpace(UUID documentSpaceId, String dashboardUserEmail);

    void removeDashboardUserFromDocumentSpace(UUID documentSpaceId, String[] emails);

    List<String> batchAddDashboardUserToDocumentSpace(UUID documentSpaceId, MultipartFile file);
}
