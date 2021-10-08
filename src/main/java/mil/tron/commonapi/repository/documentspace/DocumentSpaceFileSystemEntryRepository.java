package mil.tron.commonapi.repository.documentspace;

import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

    void deleteByDocumentSpaceIdEqualsAndItemIdEquals(UUID spaceId, UUID itemId);
}
