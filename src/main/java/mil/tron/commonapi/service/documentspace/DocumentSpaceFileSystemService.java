package mil.tron.commonapi.service.documentspace;

import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public interface DocumentSpaceFileSystemService {

    List<String> getFolderNamesUnderneath(UUID spaceId, @Nullable String folderName);
    DocumentSpaceFileSystemEntry addFolder(UUID spaceId, String name, @Nullable String parentFolderName);
    void deleteFolder(UUID spaceId, String name);

}
