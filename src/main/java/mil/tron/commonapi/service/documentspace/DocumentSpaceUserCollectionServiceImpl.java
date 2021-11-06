package mil.tron.commonapi.service.documentspace;

import mil.tron.commonapi.dto.documentspace.DocumentSpaceUserCollectionRequestDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceUserCollectionResponseDto;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceUserCollection;
import mil.tron.commonapi.exception.NotAuthorizedException;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceFileSystemEntryRepository;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceRepository;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceUserCollectionRepository;
import mil.tron.commonapi.service.DashboardUserService;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DocumentSpaceUserCollectionServiceImpl implements DocumentSpaceUserCollectionService {
    private final DocumentSpaceFileSystemEntryRepository documentSpaceFileSystemEntryRepository;

    private final DocumentSpaceUserCollectionRepository documentSpaceUserCollectionRepository;

    private final DocumentSpaceRepository documentSpaceRepository;

    private final DashboardUserService dashboardUserService;

    private final ModelMapper mapper = new ModelMapper();


    private static final String FAVORITES = "Favorites";
    private static final String COLLECTION_NOT_FOUND = "Collection not found";
    private static final String USER_NOT_FOUND = "User not found";
    private static final String NOT_AUTHORIZED_TO_DOC_SPACE = "Not Authorized to this Document Space";

    public DocumentSpaceUserCollectionServiceImpl(DocumentSpaceUserCollectionRepository documentSpaceUserCollectionRepository, DocumentSpaceRepository documentSpaceRepository, DashboardUserService dashboardUserService, DocumentSpaceFileSystemEntryRepository documentSpaceFileSystemEntryRepository) {
        this.documentSpaceUserCollectionRepository = documentSpaceUserCollectionRepository;
        this.documentSpaceRepository = documentSpaceRepository;
        this.dashboardUserService = dashboardUserService;
        this.documentSpaceFileSystemEntryRepository = documentSpaceFileSystemEntryRepository;
    }


    @Override
    public DocumentSpaceUserCollection createFavoriteCollection(DocumentSpaceUserCollectionRequestDto documentSpaceUserCollectionDto) {
        documentSpaceUserCollectionDto.setName(FAVORITES);
        return createCollection(documentSpaceUserCollectionDto);
    }

    /**
     * kept private for now to expand collections to not only be favorites but still enable favorites for now
     */
    private DocumentSpaceUserCollection createCollection(DocumentSpaceUserCollectionRequestDto documentSpaceUserCollectionDto) {

        DocumentSpaceUserCollection documentSpaceUserCollectionEntity = convertDocumentSpaceUserCollectionRequestDtoToEntity(documentSpaceUserCollectionDto);
        boolean userInDocumentSpace = documentSpaceRepository.isUserInDocumentSpace(documentSpaceUserCollectionEntity.getDashboardUserId(), documentSpaceUserCollectionEntity.getDocumentSpaceId());
        boolean collectionExistsForUserInDocumentSpace = documentSpaceUserCollectionRepository.existsByNameAndDocumentSpaceIdAndDashboardUserId(documentSpaceUserCollectionEntity.getName(), documentSpaceUserCollectionEntity.getDocumentSpaceId(), documentSpaceUserCollectionEntity.getDashboardUserId());
        if(!userInDocumentSpace){
            throw new NotAuthorizedException(NOT_AUTHORIZED_TO_DOC_SPACE);
        }else if(collectionExistsForUserInDocumentSpace){
            throw new ResourceAlreadyExistsException("Collection already exists");
        }

        return documentSpaceUserCollectionRepository.save(documentSpaceUserCollectionEntity);
    }

    @Override
    public Set<DocumentSpaceUserCollection> getCollectionsForUser(DashboardUser dashboardUser) {
        return documentSpaceUserCollectionRepository.getDocumentSpaceUserCollectionByDashboardUserIdEquals(dashboardUser.getId());
    }

    @Override
    public Set<DocumentSpaceUserCollection> getCollectionsForUser(String dashboardUserEmail) {
        DashboardUser dashboardUserByEmail = dashboardUserService.getDashboardUserByEmail(dashboardUserEmail);
        if(dashboardUserByEmail == null){
            throw new RecordNotFoundException(USER_NOT_FOUND);
        }
        return getCollectionsForUser(dashboardUserByEmail);
    }

    @Override
    public Set<DocumentSpaceUserCollectionResponseDto> getFavoriteEntriesForUserInDocumentSpace(String dashboardUserEmail, UUID documentSpaceId) {
        DashboardUser dashboardUserByEmail = dashboardUserService.getDashboardUserByEmail(dashboardUserEmail);
        if(dashboardUserByEmail == null){
            throw new RecordNotFoundException(USER_NOT_FOUND);
        }
        boolean userInDocumentSpace = documentSpaceRepository.isUserInDocumentSpace(dashboardUserByEmail.getId(), documentSpaceId);
        if(!userInDocumentSpace){
            throw new NotAuthorizedException(NOT_AUTHORIZED_TO_DOC_SPACE);
        }
        Optional<DocumentSpaceUserCollection> favorites = documentSpaceUserCollectionRepository.findDocumentSpaceUserCollectionByNameAndDocumentSpaceIdAndDashboardUserId(FAVORITES, documentSpaceId, dashboardUserByEmail.getId());

        if(favorites.isEmpty()){
            throw new RecordNotFoundException("Unable to find Favorite folder");
        }
        Set<DocumentSpaceFileSystemEntry> entries = favorites.get().getEntries();

        return entries.stream().map(this::convertEntryToResponseDto).collect(Collectors.toSet());
    }

    @Transactional
    @Override
    public void addEntityToFavoritesFolder(String dashboardUserEmail, UUID entryId, UUID documentSpaceId){
        DashboardUser dashboardUserByEmail = dashboardUserService.getDashboardUserByEmail(dashboardUserEmail);
        if(dashboardUserByEmail == null){
            throw new RecordNotFoundException(USER_NOT_FOUND);
        }
        boolean userInDocumentSpace = documentSpaceRepository.isUserInDocumentSpace(dashboardUserByEmail.getId(), documentSpaceId);
        if(!userInDocumentSpace){
            throw new NotAuthorizedException(NOT_AUTHORIZED_TO_DOC_SPACE);
        }

        Optional<DocumentSpaceUserCollection> favorites = documentSpaceUserCollectionRepository.findDocumentSpaceUserCollectionByNameAndDocumentSpaceIdAndDashboardUserId(FAVORITES,  documentSpaceId, dashboardUserByEmail.getId());

        if(favorites.isEmpty()){
            DocumentSpaceUserCollection favoriteCollection = createFavoriteCollection(DocumentSpaceUserCollectionRequestDto.builder().documentSpaceId(documentSpaceId).dashboardUserId(dashboardUserByEmail.getId()).build());
            addFileSystemEntryToCollection(entryId, favoriteCollection.getId());
        }else{
            addFileSystemEntryToCollection(entryId, favorites.get().getId());
        }
    }

    @Transactional
    @Override
    public void addFileSystemEntryToCollection(UUID entryId, UUID collectionId) {

        Optional<DocumentSpaceFileSystemEntry> entryById = documentSpaceFileSystemEntryRepository.findById(entryId);

        Optional<DocumentSpaceUserCollection> collectionById = documentSpaceUserCollectionRepository.findById(collectionId);

        if(entryById.isPresent() && collectionById.isPresent()){
            DocumentSpaceUserCollection documentSpaceUserCollection = collectionById.get();
            documentSpaceUserCollection.getEntries().add(entryById.get());
            documentSpaceUserCollectionRepository.save(documentSpaceUserCollection);
        }else if(entryById.isEmpty()){
            throw new RecordNotFoundException("FileEntry not found");
        }else if(collectionById.isEmpty()){
            throw new RecordNotFoundException(COLLECTION_NOT_FOUND);
        }
    }

    @Transactional
    @Override
    public void removeEntityFromFavoritesFolder(String dashboardUserEmail, UUID entryId, UUID documentSpaceId) {
        DashboardUser dashboardUserByEmail = dashboardUserService.getDashboardUserByEmail(dashboardUserEmail);
        if(dashboardUserByEmail == null){
            throw new RecordNotFoundException(USER_NOT_FOUND);
        }
        boolean userInDocumentSpace = documentSpaceRepository.isUserInDocumentSpace(dashboardUserByEmail.getId(), documentSpaceId);
        if(!userInDocumentSpace){
            throw new NotAuthorizedException(NOT_AUTHORIZED_TO_DOC_SPACE);
        }
        Optional<DocumentSpaceUserCollection> favorites = documentSpaceUserCollectionRepository.findDocumentSpaceUserCollectionByNameAndDocumentSpaceIdAndDashboardUserId(FAVORITES,  documentSpaceId, dashboardUserByEmail.getId());

        if(favorites.isPresent()){
            removeFileSystemEntryFromCollection(entryId, favorites.get().getId());
        }
    }

    @Transactional
    @Override
    public void removeFileSystemEntryFromCollection(UUID entryId, UUID collectionId) {
        Optional<DocumentSpaceFileSystemEntry> entryById = documentSpaceFileSystemEntryRepository.findById(entryId);

        Optional<DocumentSpaceUserCollection> collectionById = documentSpaceUserCollectionRepository.findById(collectionId);

        if(entryById.isPresent() && collectionById.isPresent()){
            DocumentSpaceUserCollection documentSpaceUserCollection = collectionById.get();
            documentSpaceUserCollection.getEntries().remove(entryById.get());
            documentSpaceUserCollectionRepository.save(documentSpaceUserCollection);
        }else if(entryById.isEmpty()){
            throw new RecordNotFoundException("FileEntry not found");
        }else if(collectionById.isEmpty()){
            throw new RecordNotFoundException(COLLECTION_NOT_FOUND);
        }
    }

    @Override
    public void removeEntityFromAllCollections(UUID entryId) {
        documentSpaceUserCollectionRepository.deleteFileSystemEntryFromCollections(entryId);
    }

    @Override
    public void deleteCollection(UUID collectionId) {
        Optional<DocumentSpaceUserCollection> byId = documentSpaceUserCollectionRepository.findById(collectionId);

        if(byId.isPresent()){
            documentSpaceUserCollectionRepository.deleteById(collectionId);
        }else{
            throw new RecordNotFoundException(COLLECTION_NOT_FOUND);
        }
    }

    private DocumentSpaceUserCollection convertDocumentSpaceUserCollectionRequestDtoToEntity(DocumentSpaceUserCollectionRequestDto dto){
        mapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);
        return mapper.map(dto, DocumentSpaceUserCollection.class);
    }

    private DocumentSpaceUserCollectionResponseDto convertEntryToResponseDto(DocumentSpaceFileSystemEntry entry){
        return DocumentSpaceUserCollectionResponseDto
                .builder()
                .id(entry.getId())
                .isFolder(entry.isFolder())
                .key(entry.getItemName())
                .spaceId(entry.getDocumentSpaceId())
                .parentId(entry.getParentEntryId())
                .lastModifiedDate(entry.getLastModifiedOn())
                .build();
    }
}
