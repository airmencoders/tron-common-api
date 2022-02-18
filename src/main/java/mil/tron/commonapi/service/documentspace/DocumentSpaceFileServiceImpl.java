package mil.tron.commonapi.service.documentspace;

import com.google.common.collect.Lists;
import mil.tron.commonapi.dto.documentspace.RecentDocumentDto;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceFileSystemEntryRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.util.*;

@Service
public class DocumentSpaceFileServiceImpl implements DocumentSpaceFileService {
	private final DocumentSpaceFileSystemEntryRepository documentSpaceFileSystemRepository;
	
	public DocumentSpaceFileServiceImpl(DocumentSpaceFileSystemEntryRepository documentSpaceFileSystemRepository) {
		this.documentSpaceFileSystemRepository = documentSpaceFileSystemRepository;
	}

	@Override
	public Optional<DocumentSpaceFileSystemEntry> getFileInDocumentSpaceFolder(UUID documentSpaceId, UUID parentFolderId, String filename) {
		return documentSpaceFileSystemRepository.findFileByDocumentSpaceIdAndParentEntryIdAndItemNameAndIsFolderFalse(documentSpaceId, parentFolderId, filename);
	}
	
	@Override
	public DocumentSpaceFileSystemEntry getFileInDocumentSpaceFolderOrThrow(UUID documentSpaceId, UUID parentFolderId,
			String filename) throws RecordNotFoundException {
		return getFileInDocumentSpaceFolder(documentSpaceId, parentFolderId, filename).orElseThrow(() -> new RecordNotFoundException("File not found: " + filename));
	}

	@Override
	public DocumentSpaceFileSystemEntry saveDocumentSpaceFile(DocumentSpaceFileSystemEntry documentSpaceFile) {
		documentSpaceFile.setFolder(false);
		
		return documentSpaceFileSystemRepository.save(documentSpaceFile);
	}

	@Override
	public DocumentSpaceFileSystemEntry renameDocumentSpaceFile(DocumentSpaceFileSystemEntry documentSpaceFile, String newName) {
		// check existence of a same-named file/folder at this path
		if (documentSpaceFileSystemRepository
				.existsByDocumentSpaceIdAndParentEntryIdAndItemNameAndIsDeleteArchivedEquals(documentSpaceFile.getDocumentSpaceId(),
						documentSpaceFile.getParentEntryId(),
						newName,
						false)) {
			throw new ResourceAlreadyExistsException("A file or folder at this path already exists with that name");
		}

		documentSpaceFile.setItemName(newName);
		return documentSpaceFileSystemRepository.save(documentSpaceFile);
	}

	@Override
	public void deleteDocumentSpaceFile(DocumentSpaceFileSystemEntry documentSpaceFile) {
		documentSpaceFileSystemRepository.delete(documentSpaceFile);
		documentSpaceFileSystemRepository.flush();
	}

	@Override
	public void deleteAllDocumentSpaceFilesInParentFolder(UUID documentSpaceId, UUID parentId) {
		documentSpaceFileSystemRepository.deleteAllEntriesByDocumentSpaceIdAndParentEntryIdAndIsFolderFalse(documentSpaceId, parentId);
		documentSpaceFileSystemRepository.flush();
	}

	@Override
	public void deleteAllDocumentSpaceFilesInParentFolderExcept(UUID documentSpaceId, UUID parentFolderId,
			Set<String> excludedFilenames) {
		documentSpaceFileSystemRepository
				.deleteAllEntriesByDocumentSpaceIdAndParentEntryIdAndItemNameNotInAndIsFolderFalse(documentSpaceId,
						parentFolderId, excludedFilenames);
		documentSpaceFileSystemRepository.flush();
	}

	@Override
	public Slice<RecentDocumentDto> getRecentlyUploadedFilesByUser(String username, Set<UUID> authorizedSpaceIds,
			Pageable pageable) {
		return documentSpaceFileSystemRepository.getRecentlyUploadedFilesByUser(username, authorizedSpaceIds, pageable);
	}

	@Override
	public Slice<RecentDocumentDto> getRecentlyUploadedFilesBySpace(UUID spaceId, @Nullable Date date, Pageable pageable) {
		if (date == null) {  // if somehow null for date, then use now
			date = new Date();
		}
		return documentSpaceFileSystemRepository.getRecentlyUploadedFilesBySpace(spaceId, date, pageable);
	}

}
