package mil.tron.commonapi.service.documentspace;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import mil.tron.commonapi.dto.documentspace.DocumentMetadata;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;
import mil.tron.commonapi.entity.documentspace.metadata.FileSystemEntryMetadata;
import mil.tron.commonapi.entity.documentspace.metadata.FileSystemEntryWithMetadata;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceUserCollectionRepository;
import mil.tron.commonapi.repository.documentspace.FileSystemEntryMetadataRepository;

@Service
public class DocumentSpaceMetadataServiceImpl implements DocumentSpaceMetadataService {
	
	private final FileSystemEntryMetadataRepository metadataRepository;
	private final DocumentSpaceUserCollectionRepository collectionRepositry;
	
	public DocumentSpaceMetadataServiceImpl(
			FileSystemEntryMetadataRepository metadataRepository,
			DocumentSpaceUserCollectionRepository collectionRepositry) {
		this.metadataRepository = metadataRepository;
		this.collectionRepositry = collectionRepositry;
	}

	@Override
	public void saveMetadata(UUID documentSpaceId, Set<DocumentSpaceFileSystemEntry> entries, DocumentMetadata metadataToChange, DashboardUser dashboardUser) {
		/**
		 * As it stands, metadata is only being scoped specifically to files in
		 * "Favorite" collection for a user. So, find only the file system entries (and
		 * its associated metadata if it exists) that belong to the favorite collection
		 * for this user and save metadata for those file system entries.
		 */
		Set<FileSystemEntryWithMetadata> entriesWithMetadataInUserFavorites = collectionRepositry.getAllInCollectionMatchingIdsAsMetadata(
				DocumentSpaceUserCollectionServiceImpl.FAVORITES, documentSpaceId, dashboardUser.getId(),
				entries.stream().map(DocumentSpaceFileSystemEntry::getId).collect(Collectors.toSet()));
		
		List<FileSystemEntryMetadata> metadataToSave = new ArrayList<>();
		
		for (FileSystemEntryWithMetadata entry : entriesWithMetadataInUserFavorites) {
			FileSystemEntryMetadata metadata = entry.getMetadata();
			
			if (metadata == null) {
				metadata = new FileSystemEntryMetadata();
				metadata.setFileSystemEntry(entry.getFileEntry());
				metadata.setDashboardUser(dashboardUser);
				metadata.setId(UUID.randomUUID());
			}
			
			if (metadataToChange.getLastDownloaded() != null) {
				metadata.setLastDownloaded(metadataToChange.getLastDownloaded());
			}
			
			metadataToSave.add(metadata);
		}
		
		metadataRepository.saveAll(metadataToSave);
	}

	@Override
	public void saveMetadata(UUID documentSpaceId, DocumentSpaceFileSystemEntry entry, DocumentMetadata metadata, DashboardUser dashboardUser) {
		Set<DocumentSpaceFileSystemEntry> entrySet = new HashSet<>();
		entrySet.add(entry);
		saveMetadata(documentSpaceId, entrySet, metadata, dashboardUser);
	}

}
