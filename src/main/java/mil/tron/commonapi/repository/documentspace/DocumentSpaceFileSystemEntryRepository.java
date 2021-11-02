package mil.tron.commonapi.repository.documentspace;

import mil.tron.commonapi.dto.documentspace.RecentDocumentDto;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    void deleteAllEntriesByDocumentSpaceIdAndParentEntryIdAndItemNameNotInAndIsFolderFalse(UUID documentSpaceId, UUID parentId, Set<String> excludedFilenames);
    
    @Query("select entry from DocumentSpaceFileSystemEntry entry"
    		+ " where entry.documentSpaceId = :documentSpaceId and entry.isFolder = false and"
    		+ " (entry.lastModifiedBy = :username OR entry.createdBy = :username)"
    		+ " order by coalesce(entry.lastModifiedOn, entry.createdOn)")
    List<DocumentSpaceFileSystemEntry> getRecentlyUploadedFilesByDocumentSpaceAndUser(UUID documentSpaceId, String username, Pageable pageable);
    
    @Query("select new mil.tron.commonapi.dto.documentspace.RecentDocumentDto("
    		+ "entry.id, entry.itemName, entry.parentEntryId, coalesce(entry.lastModifiedOn, entry.createdOn), documentSpace.id, documentSpace.name)"
    		+ " from DocumentSpaceFileSystemEntry entry, DocumentSpace documentSpace"
    		+ " where (entry.lastModifiedBy = :username OR entry.createdBy = :username) and"
    		+ " entry.isFolder = false and"
    		+ " entry.documentSpaceId = documentSpace.id and"
    		+ " documentSpace.id in :authorizedSpaceIds"
    		+ " order by coalesce(entry.lastModifiedOn, entry.createdOn) desc")
    Slice<RecentDocumentDto> getRecentlyUploadedFilesByUser(String username, Set<UUID> authorizedSpaceIds, Pageable pageable);
}
