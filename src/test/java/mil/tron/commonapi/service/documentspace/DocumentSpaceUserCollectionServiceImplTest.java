package mil.tron.commonapi.service.documentspace;

import mil.tron.commonapi.dto.documentspace.DocumentSpaceUserCollectionRequestDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceUserCollectionResponseDto;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceUserCollection;
import mil.tron.commonapi.entity.documentspace.metadata.FileSystemEntryMetadata;
import mil.tron.commonapi.entity.documentspace.metadata.FileSystemEntryWithMetadata;
import mil.tron.commonapi.exception.NotAuthorizedException;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceFileSystemEntryRepository;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceRepository;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceUserCollectionRepository;
import mil.tron.commonapi.service.DashboardUserService;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class DocumentSpaceUserCollectionServiceImplTest {

    @InjectMocks
    private DocumentSpaceUserCollectionServiceImpl collectionService;

    @Mock
    private DocumentSpaceFileSystemEntryRepository documentSpaceFileSystemEntryRepository;

    @Mock
    private DocumentSpaceUserCollectionRepository documentSpaceUserCollectionRepository;

    @Mock
    private DashboardUserService dashboardUserService;

    @Mock
    private DocumentSpaceRepository documentSpaceRepository;

    private DocumentSpaceUserCollectionRequestDto requestDto;

    private DocumentSpaceUserCollection entity;

    UUID docSpaceId, dashboardUserId;
    DashboardUser dashboardUser;
    @BeforeEach
    void setup() {
    	docSpaceId = UUID.randomUUID();
    	dashboardUserId = UUID.randomUUID();
        requestDto = DocumentSpaceUserCollectionRequestDto.builder().documentSpaceId(docSpaceId).dashboardUserId(dashboardUserId).name("test").build();
        entity = DocumentSpaceUserCollection.builder().documentSpaceId(docSpaceId).dashboardUserId(dashboardUserId).name("Favorites").entries(new HashSet<>()).id(UUID.randomUUID()).build();

        dashboardUser = DashboardUser.builder().id(dashboardUserId).email("tron@dev.com").build();
    }

    @Test
    void testCreateFavoriteCollection() {

		DocumentSpaceUserCollectionRequestDto dto = DocumentSpaceUserCollectionRequestDto.builder().documentSpaceId(docSpaceId).dashboardUserId(dashboardUserId).name("TestingName").build();

		doReturn(false).when(documentSpaceUserCollectionRepository).existsByNameAndDocumentSpaceIdAndDashboardUserId("Favorites", requestDto.getDocumentSpaceId(), requestDto.getDashboardUserId());

		doReturn(entity).when(documentSpaceUserCollectionRepository).save(Mockito.any());
		DocumentSpaceUserCollection collectionResponse = collectionService.createFavoriteCollection(dto);
		Assert.assertEquals(entity, collectionResponse);
	}

    @Test
    void testCreateFavoriteCollection_ThrowsCollectionAlreadyExists() {
		DocumentSpaceUserCollectionRequestDto dto = DocumentSpaceUserCollectionRequestDto.builder().documentSpaceId(docSpaceId).dashboardUserId(dashboardUserId).name("TestingName").build();
        doReturn(true).when(documentSpaceUserCollectionRepository).existsByNameAndDocumentSpaceIdAndDashboardUserId("Favorites", requestDto.getDocumentSpaceId(), requestDto.getDashboardUserId());
        Assertions.assertThrows(ResourceAlreadyExistsException.class, () -> collectionService.createFavoriteCollection(dto));
    }

    @Test
    void testGetCollectionForUser() {

        doReturn(Set.of(entity)).when(documentSpaceUserCollectionRepository).getDocumentSpaceUserCollectionByDashboardUserIdEquals(dashboardUser.getId());
        Set<DocumentSpaceUserCollection> collectionForUser = collectionService.getCollectionsForUser(dashboardUser);

        Assertions.assertTrue(collectionForUser.contains(entity));
    }

    @Test
    void testGetCollectionForUser_NoCollectionFound() {

        Set<DocumentSpaceUserCollection> collectionForUser = collectionService.getCollectionsForUser(dashboardUser);
        Assertions.assertEquals(0, collectionForUser.size());
    }

    @Test
    void testGetCollectionForUser_UserNotFound() {
        doReturn(null).when(dashboardUserService).getDashboardUserByEmail("not@real.email");
        Assertions.assertThrows(RecordNotFoundException.class, () -> collectionService.getCollectionsForUser("not@real.email"));
    }

    @Test
    void testAddingEntryToCollection() {
        UUID fileEntryId = UUID.randomUUID();
        UUID collectionId = entity.getId();
        DocumentSpaceFileSystemEntry fileSystemEntry = DocumentSpaceFileSystemEntry.builder().id(fileEntryId).build();

        doReturn(Optional.of(fileSystemEntry)).when(documentSpaceFileSystemEntryRepository).findById(fileEntryId);
        doReturn(Optional.of(entity)).when(documentSpaceUserCollectionRepository).findById(collectionId);

        Assertions.assertDoesNotThrow(()->collectionService.addFileSystemEntryToCollection(fileEntryId, collectionId));
    }

    @Test
    void testAddingEntryToCollection_ThrowsEntryNotFound() {
        UUID fileEntryId = UUID.randomUUID();
        UUID collectionId = entity.getId();
        DocumentSpaceFileSystemEntry fileSystemEntry = DocumentSpaceFileSystemEntry.builder().id(fileEntryId).build();

        doReturn(Optional.empty()).when(documentSpaceFileSystemEntryRepository).findById(fileEntryId);
        doReturn(Optional.of(entity)).when(documentSpaceUserCollectionRepository).findById(collectionId);


        Assertions.assertThrows(RecordNotFoundException
                .class, () -> collectionService.addFileSystemEntryToCollection(fileEntryId, collectionId));

    }

    @Test
    void testAddingEntryToCollection_ThrowsCollectionNotFound() {
        UUID fileEntryId = UUID.randomUUID();
        UUID collectionId = entity.getId();
        DocumentSpaceFileSystemEntry fileSystemEntry = DocumentSpaceFileSystemEntry.builder().id(fileEntryId).build();

        doReturn(Optional.of(fileSystemEntry)).when(documentSpaceFileSystemEntryRepository).findById(fileEntryId);
        doReturn(Optional.empty()).when(documentSpaceUserCollectionRepository).findById(collectionId);


        Assertions.assertThrows(RecordNotFoundException
                .class, () -> collectionService.addFileSystemEntryToCollection(fileEntryId, collectionId));

    }

    @Test
    void testRemoveEntryFromCollection() {
        UUID fileEntryId = UUID.randomUUID();
        UUID collectionId = entity.getId();
        DocumentSpaceFileSystemEntry fileSystemEntry = DocumentSpaceFileSystemEntry.builder().id(fileEntryId).build();

        doReturn(Optional.of(fileSystemEntry)).when(documentSpaceFileSystemEntryRepository).findById(fileEntryId);
        doReturn(Optional.of(entity)).when(documentSpaceUserCollectionRepository).findById(collectionId);

        Assertions.assertDoesNotThrow(()->collectionService.removeFileSystemEntryFromCollection(fileEntryId, collectionId));
    }

    @Test
    void testRemovingEntryToCollection_ThrowsEntryNotFound() {
        UUID fileEntryId = UUID.randomUUID();
        UUID collectionId = entity.getId();
        DocumentSpaceFileSystemEntry fileSystemEntry = DocumentSpaceFileSystemEntry.builder().id(fileEntryId).build();

        doReturn(Optional.empty()).when(documentSpaceFileSystemEntryRepository).findById(fileEntryId);
        doReturn(Optional.of(entity)).when(documentSpaceUserCollectionRepository).findById(collectionId);

        Assertions.assertThrows(RecordNotFoundException
                .class, () -> collectionService.removeFileSystemEntryFromCollection(fileEntryId, collectionId));
    }

    @Test
    void testRemovingEntryToCollection_ThrowsCollectionNotFound() {
        UUID fileEntryId = UUID.randomUUID();
        UUID collectionId = entity.getId();
        DocumentSpaceFileSystemEntry fileSystemEntry = DocumentSpaceFileSystemEntry.builder().id(fileEntryId).build();

        doReturn(Optional.of(fileSystemEntry)).when(documentSpaceFileSystemEntryRepository).findById(fileEntryId);
        doReturn(Optional.empty()).when(documentSpaceUserCollectionRepository).findById(collectionId);

        Assertions.assertThrows(RecordNotFoundException
                .class, () -> collectionService.removeFileSystemEntryFromCollection(fileEntryId, collectionId));
    }

    @Test
    void testDeletingCollection() {
        doReturn(Optional.of(entity)).when(documentSpaceUserCollectionRepository).findById(entity.getId());

        collectionService.deleteCollection(entity.getId());
		Mockito.verify(documentSpaceUserCollectionRepository, times(1)).deleteById(entity.getId());
    }

    @Test
    void testDeletingCollection_ThrowsCollectionNotFound() {
        doReturn(Optional.empty()).when(documentSpaceUserCollectionRepository).findById(entity.getId());

        Assertions.assertThrows(RecordNotFoundException
                .class, () -> collectionService.deleteCollection(entity.getId()));
    }

    @Test
    void testGetFavorites() {
        doReturn(dashboardUser).when(dashboardUserService).getDashboardUserByEmail(dashboardUser.getEmail());

        List<FileSystemEntryWithMetadata> entriesWithMetadata = new ArrayList<>();
        entity.getEntries().forEach(entry -> {
        	FileSystemEntryMetadata metadata = new FileSystemEntryMetadata();
        	entriesWithMetadata.add(new FileSystemEntryWithMetadata(entry, metadata));
        });

        Mockito.when(documentSpaceUserCollectionRepository.getAllInCollectionAsMetadata(Mockito.anyString(), Mockito.any(UUID.class), Mockito.any(UUID.class)))
        	.thenReturn(Set.copyOf(entriesWithMetadata));

        List<DocumentSpaceUserCollectionResponseDto> favoriteEntriesForUserInDocumentSpace = collectionService.getFavoriteEntriesForUserInDocumentSpace(dashboardUser.getEmail(), docSpaceId);

        Assertions.assertEquals(entity.getEntries().size(), favoriteEntriesForUserInDocumentSpace.size());
    }

    @Test
    void testGetFavorites_ThrowUserNotFound() {
        doReturn(null).when(dashboardUserService).getDashboardUserByEmail(dashboardUser.getEmail());

        Assertions.assertThrows(RecordNotFoundException.class, () -> collectionService.getFavoriteEntriesForUserInDocumentSpace(dashboardUser.getEmail(), docSpaceId));
    }

}
