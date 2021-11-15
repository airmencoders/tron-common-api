package mil.tron.commonapi.service.documentspace;

import java.util.Set;
import java.util.UUID;

import mil.tron.commonapi.dto.documentspace.DocumentMetadata;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;

public interface DocumentSpaceMetadataService {
	void saveMetadata(UUID documentSpaceId, Set<DocumentSpaceFileSystemEntry> entries, DocumentMetadata metadata, DashboardUser dashboardUser);
	void saveMetadata(UUID documentSpaceId, DocumentSpaceFileSystemEntry entry, DocumentMetadata metadata, DashboardUser dashboardUser);
}
