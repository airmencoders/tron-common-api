package mil.tron.commonapi.service.documentspace;


import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import mil.tron.commonapi.dto.documentspace.DocumentMetadata;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;
import mil.tron.commonapi.entity.documentspace.metadata.FileSystemEntryMetadata;
import mil.tron.commonapi.entity.documentspace.metadata.FileSystemEntryWithMetadata;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceUserCollectionRepository;
import mil.tron.commonapi.repository.documentspace.FileSystemEntryMetadataRepository;

@ExtendWith(MockitoExtension.class)
class DocumentSpaceMetadataServiceTest {
	@Mock
	private FileSystemEntryMetadataRepository metadataRepository;
	
	@Mock
	private DocumentSpaceUserCollectionRepository collectionRepositry;
	
	@InjectMocks
	private DocumentSpaceMetadataServiceImpl metadataService;
	
	private DocumentMetadata metadata;
	private List<DocumentSpaceFileSystemEntry> fileEntries;
	private List<FileSystemEntryWithMetadata> filesAndMetadata;
	
	private DashboardUser dashboardUser;
	
	@BeforeEach
	void setup() {
		metadata = new DocumentMetadata(new Date());
		
		fileEntries = new ArrayList<>();
		filesAndMetadata = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			DocumentSpaceFileSystemEntry entry = DocumentSpaceFileSystemEntry.builder()
					.id(UUID.randomUUID())
					.documentSpaceId(UUID.randomUUID())
					.parentEntryId(UUID.randomUUID())
					.itemName(RandomStringUtils.random(10))
					.build();
			fileEntries.add(entry);
			
			FileSystemEntryMetadata fileEntryMetadata = new FileSystemEntryMetadata();
			
			filesAndMetadata.add(new FileSystemEntryWithMetadata(entry, fileEntryMetadata));
		}
		
		// Add a single entry with no metadata
		DocumentSpaceFileSystemEntry singleEntry = DocumentSpaceFileSystemEntry.builder()
				.id(UUID.randomUUID())
				.documentSpaceId(UUID.randomUUID())
				.parentEntryId(UUID.randomUUID())
				.itemName(RandomStringUtils.random(10))
				.build();
		fileEntries.add(singleEntry);
		filesAndMetadata.add(new FileSystemEntryWithMetadata(singleEntry, null));
		
		dashboardUser = DashboardUser.builder()
				.id(UUID.randomUUID())
				.emailAsLower("test@email.com")
				.build();
	}
	
	@Nested
	class SaveMetadataTest {
		@Test
		void shouldSaveMetadata() {
			Mockito.when(collectionRepositry.getAllInCollectionMatchingIdsAsMetadata(Mockito.anyString(), Mockito.any(UUID.class), Mockito.any(UUID.class), Mockito.anySet()))
				.thenReturn(Set.copyOf(filesAndMetadata));
			
			metadataService.saveMetadata(UUID.randomUUID(), Set.copyOf(fileEntries), metadata, dashboardUser);
			
			Mockito.verify(metadataRepository).saveAll(Mockito.anyIterable());
		}
		
		@Test
		void shouldSaveSingleMetadata() {
			Mockito.when(collectionRepositry.getAllInCollectionMatchingIdsAsMetadata(Mockito.anyString(), Mockito.any(UUID.class), Mockito.any(UUID.class), Mockito.anySet()))
				.thenReturn(Set.copyOf(filesAndMetadata));
			
			metadataService.saveMetadata(UUID.randomUUID(), fileEntries.get(0), metadata, dashboardUser);
			
			Mockito.verify(metadataRepository).saveAll(Mockito.anyIterable());
		}
	}
}
