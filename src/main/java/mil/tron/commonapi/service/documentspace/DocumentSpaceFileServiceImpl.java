package mil.tron.commonapi.service.documentspace;

import java.util.List;
import java.util.UUID;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import mil.tron.commonapi.entity.documentspace.DocumentSpaceFile;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceFileRepository;

@Service
public class DocumentSpaceFileServiceImpl implements DocumentSpaceFileService {
	private final DocumentSpaceFileRepository documentSpaceFileRepository;
	
	public DocumentSpaceFileServiceImpl(DocumentSpaceFileRepository documentSpaceFileRepository) {
		this.documentSpaceFileRepository = documentSpaceFileRepository;
	}

	@Nullable
	@Override
	public DocumentSpaceFile getFileByParentFolderIdAndFilename(UUID parentId, String filename) {
		return documentSpaceFileRepository.findByParentFolder_ItemIdAndFileName(parentId, filename);
	}

	@Override
	public DocumentSpaceFile saveDocumentSpaceFile(DocumentSpaceFile documentSpaceFile) {
		return documentSpaceFileRepository.save(documentSpaceFile);
	}

	@Override
	public void deleteDocumentSpaceFile(DocumentSpaceFile documentSpaceFile) {
		documentSpaceFileRepository.delete(documentSpaceFile);
	}

	@Override
	public void deleteAllDocumentSpaceFilesInParentFolder(UUID parentId) {
		documentSpaceFileRepository.deleteAllByParentFolder_ItemId(parentId);
	}
}
