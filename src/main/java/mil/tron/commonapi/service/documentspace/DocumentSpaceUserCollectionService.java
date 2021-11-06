package mil.tron.commonapi.service.documentspace;

import mil.tron.commonapi.dto.documentspace.DocumentSpaceUserCollectionRequestDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceUserCollectionResponseDto;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceUserCollection;

import java.util.Set;
import java.util.UUID;

public interface DocumentSpaceUserCollectionService {


    DocumentSpaceUserCollection createFavoriteCollection(DocumentSpaceUserCollectionRequestDto documentSpaceUserCollection);
    Set<DocumentSpaceUserCollectionResponseDto> getFavoriteEntriesForUserInDocumentSpace(String dashboardUserEmail, UUID documentSpaceId);
    void addEntityToFavoritesFolder(String dashboardUserEmail, UUID entryId, UUID documentSpaceId);
    void removeEntityFromFavoritesFolder(String dashboardUserEmail, UUID entryId, UUID documentSpaceId);

    Set<DocumentSpaceUserCollection> getCollectionsForUser(DashboardUser dashboardUser);
    Set<DocumentSpaceUserCollection> getCollectionsForUser(String dashboardUserEmail);

    void addFileSystemEntryToCollection(UUID entryId, UUID collectionId);
    void deleteCollection(UUID collectionId);
    void removeFileSystemEntryFromCollection(UUID entryId, UUID collectionId);
    void removeEntityFromAllCollections(UUID entryId);


}
