package mil.tron.commonapi.service.documentspace;

import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public interface DocumentSpaceFileSystemService {

    String resolveFullPathAndFileToUUIDPath(UUID spaceId, String pathAndFile);
    FilePathSpec parsePathToFilePathSpec(UUID spaceId, @Nullable String path);
    FilePathSpecWithContents getFilesAndFoldersAtPath(UUID spaceId, @Nullable String path);
    FilePathSpec convertFileSystemEntityToFilePathSpec(DocumentSpaceFileSystemEntry entry);

    List<DocumentSpaceFileSystemEntry> getElementsUnderneath(UUID spaceId, @Nullable String path);
    FileSystemElementTree dumpElementTree(UUID spaceId, @Nullable String path);

    DocumentSpaceFileSystemEntry addFolder(UUID spaceId, String name, @Nullable String path);
    void deleteFolder(UUID spaceId, String path);
}
