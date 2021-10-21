package mil.tron.commonapi.service.documentspace;

import java.util.UUID;

import org.springframework.lang.Nullable;

import mil.tron.commonapi.entity.documentspace.DocumentSpaceFile;

public interface DocumentSpaceFileService {
	@Nullable
	DocumentSpaceFile getFileByParentFolderIdAndFilename(UUID parentId, String filename);
	DocumentSpaceFile saveDocumentSpaceFile(DocumentSpaceFile documentSpaceFile);
	void deleteDocumentSpaceFile(DocumentSpaceFile documentSpaceFile);
	void deleteAllDocumentSpaceFilesInParentFolder(UUID parentId);
}
