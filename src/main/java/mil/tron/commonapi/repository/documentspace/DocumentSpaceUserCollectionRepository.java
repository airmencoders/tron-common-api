package mil.tron.commonapi.repository.documentspace;

import mil.tron.commonapi.entity.documentspace.DocumentSpaceUserCollection;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface DocumentSpaceUserCollectionRepository extends CrudRepository<DocumentSpaceUserCollection, UUID> {

    boolean existsByNameAndDocumentSpaceIdAndDashboardUserId(String name, UUID documentSpaceId, UUID dashboardUserId);

    Set<DocumentSpaceUserCollection> getDocumentSpaceUserCollectionByDashboardUserIdEquals(UUID dashboardUserId);

    Optional<DocumentSpaceUserCollection> findDocumentSpaceUserCollectionByNameAndDocumentSpaceIdAndDashboardUserId(String name, UUID documentSpaceId, UUID dashboardUserId);


    @Modifying
    @Transactional
    @Query(value = "DELETE FROM document_space_user_collection_entries " +
            "WHERE document_space_user_collection_entries.file_system_entry_id = :fileEntryId", nativeQuery = true)
    void deleteFileSystemEntryFromCollections(@Param("fileEntryId")UUID  fileEntryId);
}
