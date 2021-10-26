package mil.tron.commonapi.service.documentspace;

import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;
import mil.tron.commonapi.service.documentspace.util.FilePathSpec;
import mil.tron.commonapi.service.documentspace.util.FilePathSpecWithContents;
import mil.tron.commonapi.service.documentspace.util.FileSystemElementTree;
import mil.tron.commonapi.service.documentspace.util.S3ObjectAndFilename;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public interface DocumentSpaceFileSystemService {

    FilePathSpec parsePathToFilePathSpec(UUID spaceId, @Nullable String path);
    FilePathSpecWithContents getFilesAndFoldersAtPath(UUID spaceId, @Nullable String path);
    FilePathSpec convertFileSystemEntityToFilePathSpec(DocumentSpaceFileSystemEntry entry);

    @Nullable
    DocumentSpaceFileSystemEntry getDocumentSpaceFileSystemEntryByItemId(UUID id);
    List<DocumentSpaceFileSystemEntry> getElementsUnderneath(UUID spaceId, @Nullable String path);
    FileSystemElementTree dumpElementTree(UUID spaceId, @Nullable String path);
    List<S3ObjectAndFilename> flattenTreeToS3ObjectAndFilenameList(FileSystemElementTree tree);

    DocumentSpaceFileSystemEntry addFolder(UUID spaceId, String name, @Nullable String path);
    boolean isFolder(UUID spaceId, String path, String itemName);
    void deleteFolder(UUID spaceId, String path);
    void renameFolder(UUID spaceId, String existingPath, String newFolderName);
}
