package mil.tron.commonapi.service.documentspace;

import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceFileSystemEntryRepository;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class DocumentSpaceFileSystemServiceImpl implements DocumentSpaceFileSystemService {

    @Autowired
    private DocumentSpaceRepository documentSpaceRepository;

    @Autowired
    private DocumentSpaceFileSystemEntryRepository documentSpaceFileSystemEntryRepository;

    /**
     * Helper to get the UUID of the desired parent folder (by its name)
     * @param name parent folder name
     * @return UUID (if found) of the parent folder
     * @throws
     */
    private UUID getIdOfParentFolder(UUID spaceId, String name) {
        return documentSpaceFileSystemEntryRepository.findParentFolderId(spaceId, name)
                .orElseThrow(() -> new RuntimeException(
                        String.format("Parent folder with that name not found within space %s", spaceId)));
    }

    /**
     * Gets list of folder string names within given space underneath given folder
     * @param spaceId docu space UUID
     * @param folderName parent folder name, or null for root level of space
     * @return list of string folder names
     */
    public List<String> getFolderNamesUnderneath(UUID spaceId, String folderName) {

        if (folderName == null)
            return documentSpaceFileSystemEntryRepository.findFoldersUnderneathRoot(spaceId);
        else
            return documentSpaceFileSystemEntryRepository.findFoldersUnderneath(spaceId, folderName);
    }

    /**
     * Attempts to add a folder entry to given document space underneath given parent folder name
     * @param spaceId docu space UUID
     * @param name name of the new folder
     * @param parentFolderName name of parent folder to nest under - null if to place at root level of space
     * @return the created doc space folder/filesystem entry
     */
    @Override
    public DocumentSpaceFileSystemEntry addFolder(UUID spaceId, String name, String parentFolderName) {

        if (!documentSpaceRepository.existsById(spaceId)) throw new RuntimeException("Space not found");

        UUID desiredParentUUID = parentFolderName == null ?
                UUID.fromString(DocumentSpaceFileSystemEntry.NIL_UUID)
                : getIdOfParentFolder(spaceId, parentFolderName);

        // check no existence of duplicate -- so we don't get a nasty DB exception
        if (documentSpaceFileSystemEntryRepository.existsByDocumentSpaceIdAndParentEntryIdAndItemName(spaceId, desiredParentUUID, name)) {
            throw new RuntimeException(String.format("Folder with name %s already exists under that parent", name));
        }

        return documentSpaceFileSystemEntryRepository.save(DocumentSpaceFileSystemEntry.builder()
                .documentSpaceId(spaceId)
                .parentEntryId(desiredParentUUID)
                .itemId(UUID.randomUUID()) // assign UUID to it (gets auto assigned anyways if omitted)
                .itemName(name)
                .build());
    }

    @Override
    public void deleteFolder(UUID spaceId, String name) {

    }
}
