package mil.tron.commonapi.repository.documentspace;

import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentSpaceFileSystemEntryRepository extends JpaRepository<DocumentSpaceFileSystemEntry, UUID> {

    boolean existsByDocumentSpaceIdAndParentEntryIdAndItemName(UUID spaceId, UUID parentEntryId, String itemName);
    Optional<DocumentSpaceFileSystemEntry> findByItemIdEquals(UUID itemId);
    Optional<DocumentSpaceFileSystemEntry> findByDocumentSpaceIdEqualsAndItemNameEquals(UUID spaceId, String name);
    Optional<DocumentSpaceFileSystemEntry> findByDocumentSpaceIdEqualsAndItemNameEqualsAndParentEntryIdEquals(UUID spaceId, String folderName, UUID parentId);
    Optional<DocumentSpaceFileSystemEntry> findByDocumentSpaceIdEqualsAndItemIdEquals(UUID spaceId, UUID itemId);
    List<DocumentSpaceFileSystemEntry> findByDocumentSpaceIdEqualsAndParentEntryIdEquals(UUID spaceId, UUID parentId);
    List<DocumentSpaceFileSystemEntry> findByDocumentSpaceIdEqualsAndParentEntryIdEqualsAndItemIdIsNot(UUID spaceId, UUID parentId, UUID itemId);

    void deleteByDocumentSpaceIdEqualsAndItemIdEquals(UUID spaceId, UUID itemId);
    
    /*
     * Folder methods
     */
    boolean existsByDocumentSpaceIdAndParentEntryIdAndItemNameAndIsFolderTrue(UUID spaceId, UUID parentEntryId, String itemName);
    List<DocumentSpaceFileSystemEntry> findByDocumentSpaceIdEqualsAndParentEntryIdEqualsAndIsFolderTrue(UUID spaceId, UUID parentId);
    
    /*
     * File Methods
     */
    Optional<DocumentSpaceFileSystemEntry> findFileByDocumentSpaceIdAndParentEntryIdAndItemNameAndIsFolderFalse(UUID documentSpaceId, UUID parentId, String itemName);
    void deleteAllEntriesByDocumentSpaceIdAndParentEntryIdAndIsFolderFalse(UUID documentSpaceId, UUID parentId);
    void deleteAllEntriesByDocumentSpaceIdAndParentEntryIdAndItemNameNotInAndIsFolderFalse(UUID documentSpaceId, UUID parentId, List<String> excludedFilenames);
}
