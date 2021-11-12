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

    boolean existsByDocumentSpaceIdAndParentEntryIdAndItemNameAndIsDeleteArchivedEquals(UUID spaceId, UUID parentEntryId, String itemName, boolean archived);
    Optional<DocumentSpaceFileSystemEntry> findByItemIdEquals(UUID itemId);
    Optional<DocumentSpaceFileSystemEntry> findByDocumentSpaceIdEqualsAndItemNameEqualsAndParentEntryIdEquals(UUID spaceId, String folderName, UUID parentId);
    Optional<DocumentSpaceFileSystemEntry> findByDocumentSpaceIdEqualsAndItemNameEqualsAndParentEntryIdEqualsAndIsDeleteArchivedEquals(UUID spaceId, String folderName, UUID parentId, boolean archived);
    Optional<DocumentSpaceFileSystemEntry> findByDocumentSpaceIdEqualsAndItemIdEquals(UUID spaceId, UUID itemId);
    Optional<DocumentSpaceFileSystemEntry> findByDocumentSpaceIdAndItemIdAndIsFolder(UUID spaceId, UUID itemId, boolean isFolder);
    List<DocumentSpaceFileSystemEntry> findByDocumentSpaceIdEqualsAndParentEntryIdEquals(UUID spaceId, UUID parentId);
    List<DocumentSpaceFileSystemEntry> findByDocumentSpaceIdEqualsAndParentEntryIdEqualsAndIsDeleteArchivedEquals(UUID spaceId, UUID parentId, boolean archived);

    void deleteByDocumentSpaceIdEqualsAndItemIdEquals(UUID spaceId, UUID itemId);
    
    /*
     * Folder methods
     */
    boolean existsByDocumentSpaceIdAndParentEntryIdAndItemNameAndIsFolderTrue(UUID spaceId, UUID parentEntryId, String itemName);
    List<DocumentSpaceFileSystemEntry> findByDocumentSpaceIdEqualsAndParentEntryIdEqualsAndIsFolderTrue(UUID spaceId, UUID parentId);

    /*
     * File Methods
     */
    List<DocumentSpaceFileSystemEntry> findByDocumentSpaceIdAndParentEntryIdAndIsFolderFalse(UUID spaceId, UUID parentId);
    Optional<DocumentSpaceFileSystemEntry> findFileByDocumentSpaceIdAndParentEntryIdAndItemNameAndIsFolderFalse(UUID documentSpaceId, UUID parentId, String itemName);
    void deleteAllEntriesByDocumentSpaceIdAndParentEntryIdAndIsFolderFalse(UUID documentSpaceId, UUID parentId);
    void deleteAllEntriesByDocumentSpaceIdAndParentEntryIdAndItemNameNotInAndIsFolderFalse(UUID documentSpaceId, UUID parentId, Set<String> excludedFilenames);
    
    /**
     * Finds all Entries in which the last modified by or created by fields are equal to {@link username}.
     * The Entries retrieved must:
     * not be a folder,
     * must not be delete archived,
     * and must belong to a document space id found in {@link authorizedSpaceIds}
     * @param username the username to find entries by
     * @param authorizedSpaceIds the document space ids to search in
     * @param pageable page information
     * @return a {@link Slice} containing the retrieved entries
     */
    @Query("select new mil.tron.commonapi.dto.documentspace.RecentDocumentDto("
    		+ "entry.id, entry.itemName, entry.parentEntryId, coalesce(entry.lastModifiedOn, entry.createdOn), documentSpace.id, documentSpace.name)"
    		+ " from DocumentSpaceFileSystemEntry entry, DocumentSpace documentSpace"
    		+ " where (entry.lastModifiedBy = :username OR entry.createdBy = :username) and"
    		+ " entry.isFolder = false and"
    		+ " entry.isDeleteArchived = false and "
    		+ " entry.documentSpaceId = documentSpace.id and"
    		+ " documentSpace.id in :authorizedSpaceIds"
    		+ " order by coalesce(entry.lastModifiedOn, entry.createdOn) desc")
    Slice<RecentDocumentDto> getRecentlyUploadedFilesByUser(String username, Set<UUID> authorizedSpaceIds, Pageable pageable);
}
