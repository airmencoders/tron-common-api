package mil.tron.commonapi.service.documentspace;

import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceFileSystemEntryRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
	public DocumentSpaceFileSystemEntry saveDocumentSpaceFile(DocumentSpaceFileSystemEntry documentSpaceFile) {
		documentSpaceFile.setFolder(false);
		
		return documentSpaceFileSystemRepository.save(documentSpaceFile);
	}

	@Override
	public void deleteDocumentSpaceFile(DocumentSpaceFileSystemEntry documentSpaceFile) {
		documentSpaceFileSystemRepository.delete(documentSpaceFile);
	}

	@Override
	public void deleteAllDocumentSpaceFilesInParentFolder(UUID documentSpaceId, UUID parentId) {
		documentSpaceFileSystemRepository.deleteAllEntriesByDocumentSpaceIdAndParentEntryIdAndIsFolderFalse(documentSpaceId, parentId);
	}

	@Override
	public void deleteAllDocumentSpaceFilesInParentFolderExcept(UUID documentSpaceId, UUID parentFolderId,
			Set<String> excludedFilenames) {
		documentSpaceFileSystemRepository
				.deleteAllEntriesByDocumentSpaceIdAndParentEntryIdAndItemNameNotInAndIsFolderFalse(documentSpaceId,
						parentFolderId, excludedFilenames);
	}
}
