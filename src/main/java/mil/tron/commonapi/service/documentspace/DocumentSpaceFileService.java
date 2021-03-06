package mil.tron.commonapi.service.documentspace;

import mil.tron.commonapi.dto.documentspace.RecentDocumentDto;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;
import mil.tron.commonapi.exception.RecordNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface DocumentSpaceFileService {
	/**
	 * Gets a File entry in a folder of the specified Document Space.
	 * 
	 * @param documentSpaceId the document space id
	 * @param parentFolderId  the folder this file belongs to
	 * @param filename        the name of the file to find
	 * @return the file
	 */
	Optional<DocumentSpaceFileSystemEntry> getFileInDocumentSpaceFolder(UUID documentSpaceId, UUID parentFolderId,
			String filename);
	
	/**
	 * 
	 * @param documentSpaceId the document space id that this file belongs to
	 * @param parentFolderId the parent folder id of the file
	 * @param filename the name of the file to find
	 * @return the file entry
	 * @throws RecordNotFoundException if file is not found
	 */
	DocumentSpaceFileSystemEntry getFileInDocumentSpaceFolderOrThrow(UUID documentSpaceId, UUID parentFolderId,
			String filename) throws RecordNotFoundException;

	/**
	 * Saves a Document Space File
	 * 
	 * @param documentSpaceFile the file to save
	 * @return the saved entity
	 */
	DocumentSpaceFileSystemEntry saveDocumentSpaceFile(DocumentSpaceFileSystemEntry documentSpaceFile);

	/**
	 * Renames a Document Space File
	 * @param documentSpaceFile the file to change the name of
	 * @param newName the new filename
	 * @return
	 */
	DocumentSpaceFileSystemEntry renameDocumentSpaceFile(DocumentSpaceFileSystemEntry documentSpaceFile, String newName);

	/**
	 * Deletes a file from a Document Space
	 * 
	 * @param documentSpaceFile the file to delete
	 */
	void deleteDocumentSpaceFile(DocumentSpaceFileSystemEntry documentSpaceFile);

	/**
	 * Deletes all files from a Document Space belonging to a specific folder. Will
	 * only delete files within the specific parent folder. It will not propagate
	 * deletions to sub-folders of the parent.
	 * 
	 * @param documentSpaceId the document space id
	 * @param parentFolderId  the parent folder to delete all files from
	 */
	void deleteAllDocumentSpaceFilesInParentFolder(UUID documentSpaceId, UUID parentFolderId);

	/**
	 * Deletes all files from a Document Space belonging to a specific folder except
	 * for files matching names in {@link excludedFilenames}. Will only delete
	 * files within the specific parent folder. It will not propagate deletions to
	 * sub-folders of the parent.
	 * 
	 * @param documentSpaceId   the document space id
	 * @param parentFolderId    the parent folder
	 * @param excludedFilenames the file names to exclude from deletion
	 */
	void deleteAllDocumentSpaceFilesInParentFolderExcept(UUID documentSpaceId, UUID parentFolderId,
			Set<String> excludedFilenames);

	/**
	 * Returns a list of Files that a User has recently uploaded to any Document Space.
	 * @param username the username of the User
	 * @param authorizedSpaceIds list of spaceIds user can at least read from
	 * @param pageable pageable object
	 * @return a list of recently uploaded File entries
	 */
	Page<RecentDocumentDto> getRecentlyUploadedFilesByUser(String username, Set<UUID> authorizedSpaceIds, Pageable pageable);

	/**
	 * Returns a list of Files that a space has most recently had updated/uploaded.  Assumes requester
	 * has already been vetted for read access to the provided space.
	 * @param spaceId the UUID of the document space
	 * @param date the date to start search from (looking back)
	 * @param pageable the pageable object
	 * @return a list of recently uploaded File entries
	 */
	Page<RecentDocumentDto> getRecentlyUploadedFilesBySpace(UUID spaceId, Date date, Pageable pageable);
}
