package mil.tron.commonapi.repository.documentspace;

import mil.tron.commonapi.entity.documentspace.metadata.FileSystemEntryMetadata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Set;
import java.util.UUID;

public interface FileSystemEntryMetadataRepository extends JpaRepository<FileSystemEntryMetadata, UUID> {
	/**
	 * Deletes all metadata entries from files that are in the specified document space
	 * under the specified parent folder. This will not traverse any of the subfolders.
	 * It will only delete metadata located as a direct descendant of the specified 
	 * parent folder.
	 * 
	 * @param documentSpaceId the document space to delete from
	 * @param parentFolderId the parent folder of the file descendants to delete metadata from
	 */
    @Modifying
    @Query(
        	"DELETE from FileSystemEntryMetadata metadata"
        	+ " WHERE metadata.fileSystemEntry IN"
	    		+ " (SELECT fileEntry FROM DocumentSpaceFileSystemEntry fileEntry"
	    			+ " WHERE fileEntry.documentSpaceId = :documentSpaceId"
	    			+ " AND fileEntry.parentEntryId = :parentFolderId"
	    			+ " AND fileEntry.isFolder = false)"
    	)
    void removeAllFileMetadataFromDocumentSpaceInParentFolder(UUID documentSpaceId, UUID parentFolderId);
    
	/**
	 * Deletes metadata entries from files that are in the specified document space
	 * under the specified parent folder. This will not traverse any of the
	 * subfolders. It will only delete metadata located as a direct descendant of
	 * the specified parent folder. Any metadata associated with an entry with a
	 * name in {@link excludedFilenames} will be excluded from deletion.
	 * 
	 * @param documentSpaceId the document space to delete from
	 * @param parentFolderId the parent folder of the file descendants to delete metadata from
	 * @param excludedFilenames a list of filenames to exclude from metadata deletion
	 */
    @Modifying
    @Query(
        	"DELETE from FileSystemEntryMetadata metadata"
        	+ " WHERE metadata.fileSystemEntry IN"
	    		+ " (SELECT fileEntry FROM DocumentSpaceFileSystemEntry fileEntry"
		    		+ " WHERE fileEntry.documentSpaceId = :documentSpaceId"
		    		+ " AND fileEntry.parentEntryId = :parentFolderId"
		    		+ " AND fileEntry.itemName NOT IN :excludedFilenames"
		    		+ " AND fileEntry.isFolder = false)"
    	)
    void removeAllFileMetadataFromDocumentSpaceInParentFolderExcludingFilenames(UUID documentSpaceId, UUID parentFolderId, Set<String> excludedFilenames);
    
    void deleteAllByDashboardUser_Id(UUID dashboardUserId);
	
	@Query(
			"select metadata from FileSystemEntryMetadata metadata"
			+ " join fetch metadata.fileSystemEntry fileEntry where"
			+ " fileEntry.id in :fileSystemEntryIds and metadata.dashboardUser.emailAsLower = lower(:dashboardUserEmail)"
		)
	Set<FileSystemEntryMetadata> getAllByFileSystemEntryIdsAndDashboardUserEmailAsLower(Set<UUID> fileSystemEntryIds, String dashboardUserEmail);
}
