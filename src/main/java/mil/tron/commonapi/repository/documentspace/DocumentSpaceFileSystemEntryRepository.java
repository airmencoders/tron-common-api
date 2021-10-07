package mil.tron.commonapi.repository.documentspace;

import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentSpaceFileSystemEntryRepository extends JpaRepository<DocumentSpaceFileSystemEntry, UUID> {

    boolean existsByDocumentSpaceIdAndParentEntryIdAndItemName(UUID spaceId, UUID parentEntryId, String itemName);

    /**
     * Find (within a given space) the folder id whose name matches that given
     * @param spaceId doc space UUID
     * @param name folder name to find
     * @return optional containing the item's UUID
     */
    @Query(value = "select item_id from file_system_entries where doc_space_id = :spaceId and item_name = :name", nativeQuery = true)
    Optional<UUID> findParentFolderId(UUID spaceId, String name);

    /**
     * Get list of folders within a given space whose parent has the name 'parentFolderName'
     * @param spaceId doc space UUID
     * @param parentFolderName name of the parent folder
     * @return list of string names (the subfolders) under given parent name
     */
    @Query(value = "select item_name from file_system_entries where doc_space_id = :spaceId and " +
            "parent_entry_id = (select item_id from file_system_entries where doc_space_id = :spaceId and " +
            "item_name = :parentFolderName)", nativeQuery = true)
    List<String> findFoldersUnderneath(UUID spaceId, String parentFolderName);

    @Query(value = "select item_name from file_system_entries where doc_space_id = :spaceId and " +
            "parent_entry_id = '" + DocumentSpaceFileSystemEntry.NIL_UUID + "'", nativeQuery = true)
    List<String> findFoldersUnderneathRoot(UUID spaceId);
}
