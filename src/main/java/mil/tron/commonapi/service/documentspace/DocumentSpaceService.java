package mil.tron.commonapi.service.documentspace;

import com.amazonaws.services.s3.model.MultiObjectDeleteException.DeleteError;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import mil.tron.commonapi.dto.documentspace.*;
import mil.tron.commonapi.dto.documentspace.mobile.DocumentMobileDto;
import mil.tron.commonapi.entity.documentspace.DocumentSpace;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.service.documentspace.util.FilePathSpec;
import mil.tron.commonapi.service.documentspace.util.FilePathSpecWithContents;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import java.io.OutputStream;
import java.security.Principal;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface DocumentSpaceService {
    List<DocumentSpaceResponseDto> listSpaces(String username);
    DocumentSpaceResponseDto createSpace(DocumentSpaceRequestDto dto);
    
    DocumentSpace getDocumentSpaceOrElseThrow(UUID documentSpaceId) throws RecordNotFoundException;
    
    /**
     * Deletes a space from database and all files associated with it in the bucket
     * @param documentSpaceId the ID of the document space to delete
     */
    void deleteSpace(UUID documentSpaceId);

	S3Object getFile(UUID documentSpaceId, String path, String key, String documentSpaceUsername);
	S3Object getFile(UUID doucmentSpaceId, UUID parentFolderId, String filename, String documentSpaceUsername);
	void downloadAllInSpaceAndCompress(UUID documentSpaceId, OutputStream out);
	List<S3Object> getFiles(UUID documentSpaceId, String path, Set<String> fileKeys, String documentSpaceUsername);
	void downloadAndWriteCompressedFiles(UUID documentSpaceId, String path, Set<String> fileKeys, OutputStream out, String documentSpaceUsername);
	void uploadFile(UUID documentSpaceId, String path, MultipartFile file, @NotNull Date lastModified);
	void uploadFile(UUID documentSpaceId, String path, MultipartFile file);
	void renameFile(UUID documentSpaceId, String path, String fileKey, String newName);
    void deleteFile(UUID documentSpaceId, String path, String fileKey);
    void deleteFile(UUID documentSpaceId, UUID parentFolderId, String filename);
    void archiveItem(UUID documentSpaceId, UUID parentFolderId, String name);
    void moveOrCopyFile(UUID documentSpaceId, UUID sourceSpaceId, String source, String dest, boolean copy);
    void moveFile(UUID documentSpaceId, @Nullable UUID sourceSpaceId, String source, String dest);
    void copyFile(UUID documentSpaceId, @Nullable UUID sourceSpaceId, String source, String dest);
    void archiveItems(UUID documentSpaceId, String currentPath, List<String> items);
    void unArchiveItems(UUID documentSpaceId, List<String> items);
    void deleteItems(UUID documentSpaceId, String currentPath, List<String> items);
    void renameFolder(UUID documentSpaceId, String pathAndFolder, String newFolderName);
    void deleteS3ObjectByKey(String objKey);
    List<DeleteError> deleteS3ObjectsByKey(String[] objKeys);
    S3PaginationDto listFiles(UUID documentSpaceId, String continuationToken, Integer limit);
    FilePathSpec statFileAtPath(UUID documentSpaceId, String path, String element);
    DocumentSpaceFolderInfoDto getFolderSize(UUID documentSpaceId, String pathWithFolderName);
    List<S3ObjectSummary> getAllFilesInFolder(UUID documentSpaceId, String prefix, boolean includeArchived);
    FilePathSpec createFolder(UUID documentSpaceId, String path, String name);
    FilePathSpecWithContents getFolderContents(UUID documentSpaceId, String path);
    List<DocumentDto> getArchivedContents(UUID documentSpaceId);
    List<DocumentDto> getAllArchivedContentsForAuthUser(Principal principal);
    
    DocumentDto convertS3SummaryToDto(String spaceName, UUID documentSpaceId, S3ObjectSummary objSummary);

    DocumentSpace convertDocumentSpaceRequestDtoToEntity(DocumentSpaceRequestDto documentSpaceInfoDto);
    DocumentSpaceResponseDto convertDocumentSpaceEntityToResponseDto(DocumentSpace documentSpace);
    
    void addDashboardUserToDocumentSpace(UUID documentSpaceId, DocumentSpaceDashboardMemberRequestDto documentSpaceMemberDto);

    Page<DocumentSpaceDashboardMemberResponseDto> getDashboardUsersForDocumentSpace(UUID documentSpaceId, Pageable pageable);

    List<DocumentSpacePrivilegeDto> getDashboardUserPrivilegesForDocumentSpace(UUID documentSpaceId, String dashboardUserEmail);

    void removeDashboardUserFromDocumentSpace(UUID documentSpaceId, String[] emails);

    List<String> batchAddDashboardUserToDocumentSpace(UUID documentSpaceId, MultipartFile file);

    void setDashboardUserDefaultDocumentSpace(UUID documentSpaceId, String dashboardUserEmail);

    void unsetDashboardUsersDefaultDocumentSpace(DocumentSpace documentSpace);
    
    Slice<RecentDocumentDto> getRecentlyUploadedFilesByAuthUser(String authenticatedUsername, Pageable pageable);
    Slice<RecentDocumentDto> getRecentlyUploadedFilesBySpace(UUID spaceId, Date date, Pageable pageable);

    Page<DocumentMobileDto> findFilesInSpaceLike(UUID spaceId, String filename, Pageable pageable, Principal principal);
    DocumentMobileDto convertFileSystemEntryToMobileDto(UUID spaceId, DocumentSpaceFileSystemEntry entry, List<DocumentSpaceUserCollectionResponseDto> favs, Principal principal);
}
