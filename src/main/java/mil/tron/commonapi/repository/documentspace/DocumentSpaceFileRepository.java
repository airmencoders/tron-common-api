package mil.tron.commonapi.repository.documentspace;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.Nullable;

import mil.tron.commonapi.entity.documentspace.DocumentSpaceFile;

public interface DocumentSpaceFileRepository extends JpaRepository<DocumentSpaceFile, UUID> {
	@Nullable
	DocumentSpaceFile findByParentFolder_ItemIdAndFileName(UUID itemId, String fileName);
	void deleteAllByParentFolder_ItemId(UUID itemId);
}
