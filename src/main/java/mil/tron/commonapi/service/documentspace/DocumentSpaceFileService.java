package mil.tron.commonapi.service.documentspace;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import mil.tron.commonapi.dto.documentspace.RecentDocumentDto;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;

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
	 * Saves a Document Space File
	 * 
	 * @param documentSpaceFile the file to save
	 * @return the saved entity
	 */
	DocumentSpaceFileSystemEntry saveDocumentSpaceFile(DocumentSpaceFileSystemEntry documentSpaceFile);

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
	 * @param size the amount of File records to return
	 * @return a list of recently uploaded File entries
	 */
	Slice<RecentDocumentDto> getRecentlyUploadedFilesByUser(String username, Set<UUID> authorizedSpaceIds, Pageable pageable);
}
