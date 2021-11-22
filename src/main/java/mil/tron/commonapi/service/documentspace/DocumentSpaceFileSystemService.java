package mil.tron.commonapi.service.documentspace;

import mil.tron.commonapi.dto.documentspace.DocumentDto;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;
import mil.tron.commonapi.service.documentspace.util.*;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public interface DocumentSpaceFileSystemService {

	/**
	 * Retrieves the {@link FilePathSpec} for a folder
	 * @param documentSpaceId the document space id that this item belongs to
	 * @param itemId the id of the folder to retrieve info for
	 * @return the {@link FilePathSpec} for item
	 */
	FilePathSpec getFilePathSpec(UUID documentSpaceId, UUID itemId);
    FilePathSpec parsePathToFilePathSpec(UUID spaceId, @Nullable String path);
    FilePathSpecWithContents getFilesAndFoldersAtPath(UUID spaceId, @Nullable String path);
    List<DocumentDto> getArchivedItems(UUID spaceId);
    FilePathSpec convertFileSystemEntityToFilePathSpec(DocumentSpaceFileSystemEntry entry);

    String getFilePath(UUID documentSpaceId, UUID itemId);

    List<DocumentSpaceFileSystemEntry> getElementsUnderneath(UUID spaceId, @Nullable String path);
    FileSystemElementTree dumpElementTree(UUID spaceId, @Nullable String path);
    List<S3ObjectAndFilename> flattenTreeToS3ObjectAndFilenameList(FileSystemElementTree tree);

    DocumentSpaceFileSystemEntry addFolder(UUID spaceId, String name, @Nullable String path);
    boolean isFolder(UUID spaceId, String path, String itemName);
    boolean isArchived(UUID spaceId, UUID parentId, String itemName);
    void archiveElement(UUID spaceId, String path, String itemName);
    void unArchiveElements(UUID spaceId, List<String> items);
    void deleteFolder(UUID spaceId, String path);
    void renameFolder(UUID spaceId, String existingPath, String newFolderName);
    
    List<DocumentSpaceFileSystemEntry> propagateModificationStateToAncestors(DocumentSpaceFileSystemEntry propagateFrom);
    
}
