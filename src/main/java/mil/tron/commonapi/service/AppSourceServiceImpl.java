package mil.tron.commonapi.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import mil.tron.commonapi.dto.AppClientUserPrivDto;
import mil.tron.commonapi.dto.appsource.AppSourceDetailsDto;
import mil.tron.commonapi.dto.appsource.AppSourceDto;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.appsource.AppEndpoint;
import mil.tron.commonapi.entity.appsource.AppEndpointPriv;
import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.appsource.AppEndpointRepository;
import mil.tron.commonapi.repository.appsource.AppSourcePrivRepository;
import mil.tron.commonapi.repository.appsource.AppSourceRepository;

@Service
public class AppSourceServiceImpl implements AppSourceService {

    private AppSourceRepository appSourceRepository;
    private AppSourcePrivRepository appSourcePrivRepository;
    private AppEndpointRepository appEndpointRepository;
    private AppClientUserRespository appClientUserRespository;

    @Autowired
    public AppSourceServiceImpl(AppSourceRepository appSourceRepository,
                                AppSourcePrivRepository appSourcePrivRepository,
                                AppEndpointRepository appEndpointRepository,
                                AppClientUserRespository appClientUserRespository) {
        this.appSourceRepository = appSourceRepository;
        this.appSourcePrivRepository = appSourcePrivRepository;
        this.appEndpointRepository = appEndpointRepository;
        this.appClientUserRespository = appClientUserRespository;
    }

    @Override
    public List<AppSourceDto> getAppSources() {
        Iterable<AppSource> appSources = this.appSourceRepository.findAll();
        return StreamSupport
                .stream(appSources.spliterator(), false)
                .map(appSource -> AppSourceDto.builder().id(appSource.getId())
                        .name(appSource.getName())
                        .endpointCount(appSource.getAppEndpoints().size())
                        .clientCount(appSource.getAppPrivs().size())
                        .build()).collect(Collectors.toList());
    }

    public AppSourceDetailsDto createAppSource(AppSourceDetailsDto appSource) {
        return this.saveAppSource(null, appSource);
    }

    @Override
    public AppSourceDetailsDto getAppSource(UUID id) {
        Optional<AppSource> appSourceRecord = this.appSourceRepository.findById(id);
        if (appSourceRecord.isEmpty()) {
            throw new RecordNotFoundException(String.format("App Source with id %s was not found.", id));
        }
        AppSource appSource = appSourceRecord.get();
        return AppSourceDetailsDto.builder()
                .id(appSource.getId())
                .name(appSource.getName())
                .appClients(appSource.getAppPrivs().stream()
                        .map(appEndpointPriv -> AppClientUserPrivDto.builder()
                                .appClientUser(appEndpointPriv.getAppClientUser().getId())
                                .appClientUserName(appEndpointPriv.getAppClientUser().getName())
                                .privilege(appEndpointPriv.getAppEndpoint().getPath())
                                .build()).collect(Collectors.toList()))
                .build();
    }

    @Override
    public AppSourceDetailsDto updateAppSource(UUID id, AppSourceDetailsDto appSourceDetailsDto) {
        // validate id
        if (!id.equals(appSourceDetailsDto.getId())) {
            throw new InvalidRecordUpdateRequest(String.format("ID: %s does not match the resource ID: %s",
                    id, appSourceDetailsDto.getId()));
        }

        AppSource existingAppSource = this.appSourceRepository.findById(id).orElseThrow(() -> new RecordNotFoundException(String.format("No App Source found with id %s.", id)));
        // Name has changed. Make sure the new name doesn't exist
        if (!existingAppSource.getName().equals(appSourceDetailsDto.getName().trim()) &&
            this.appSourceRepository.existsByNameIgnoreCase(appSourceDetailsDto.getName().trim())) {
            throw new ResourceAlreadyExistsException("App Source with that name already exists.");
        }

        return this.saveAppSource(id, appSourceDetailsDto);
    }

    @Transactional
    @Override
    public AppSourceDetailsDto deleteAppSource(UUID id) {
        // validate id
        AppSource toRemove = this.appSourceRepository.findById(id)
                .orElseThrow(() -> new RecordNotFoundException(String.format("No App Source found with id %s.", id)));

        // remove privileges associated with the app source
        this.appSourcePrivRepository.removeAllByAppSource(AppSource.builder().id(id).build());
        this.appSourceRepository.deleteById(toRemove.getId());
        return this.buildAppSourceDetailsDto(toRemove);
    }

    // private Set<Privilege> buildPrivilegeSet(List<Long> privilegeIds, UUID appClientId) throws RecordNotFoundException {
    //     return privilegeIds.stream().map(privId -> {
    //         if (!this.privilegeRepository.existsById(privId)) {
    //             throw new RecordNotFoundException(String.format("No privilege %x found for" +
    //                     "client app with id %s.", privId, appClientId.toString()));
    //         }
    //         return Privilege.builder().id(privId).build();
    //     }).collect(Collectors.toSet());
    // }

    private AppClientUser buildAppClientUser(UUID appClientId) throws RecordNotFoundException {
        AppClientUser appClientUser = this.appClientUserRespository.findById(appClientId)
        		.orElseThrow(() -> new RecordNotFoundException(String.format("No app client with id %s found.", appClientId)));
        
        return AppClientUser.builder().id(appClientUser.getId()).name(appClientUser.getName()).build();
    }

    // private List<Long> buildPrivilegeIds(Set<Privilege> privileges) {
    //     if (privileges == null || privileges.size() == 0) {
    //         return new ArrayList<>();
    //     }
    //     return privileges.stream()
    //             .map(privilege -> privilege.getId()).collect(Collectors.toList());
    // }

    private AppEndpoint buildAppEndpoint(UUID appEndpointId) throws RecordNotFoundException {
        AppEndpoint appEndpoint = this.appEndpointRepository.findById(appEndpointId)
            .orElseThrow(() -> new RecordNotFoundException(String.format("No app endpoint with id %s found.", appEndpointId)));

        return AppEndpoint.builder()
            .id(appEndpointId)
            .method(appEndpoint.getMethod())
            .path(appEndpoint.getPath())
            .build();
    }

    private AppSourceDetailsDto buildAppSourceDetailsDto(AppSource appSource) {
        return AppSourceDetailsDto.builder()
                .id(appSource.getId())
                .name(appSource.getName())
                .appClients(appSource.getAppEndpoints().stream()
                    .map(appEndpoint -> appEndpoint.getAppEndpointPrivs().stream()
                        .map(appSourcePriv ->
                            AppClientUserPrivDto.builder()
                                .appClientUser(appSourcePriv.getAppClientUser().getId())
                                .appEndpoint(appEndpoint.getId())
                                .build())
                        .collect(Collectors.toList()))
                    .flatMap(List::stream)
                    .collect(Collectors.toList()))
                .build();
    }

    private AppSourceDetailsDto saveAppSource(UUID uuid, AppSourceDetailsDto appSource) {
        AppSource appSourceToSave = uuid != null ? 
            this.appSourceRepository.findById(uuid).orElse(AppSource.builder().id(uuid).build()) :
            AppSource.builder().id(UUID.randomUUID()).build();

        appSourceToSave.setName(appSource.getName());

        Set<AppEndpointPriv> appEndpointPrivs = appSource.getAppClients()
                .stream().map(privDto -> AppEndpointPriv.builder()
                        .appSource(appSourceToSave)
                        .appClientUser(this.buildAppClientUser(privDto.getAppClientUser()))
                        .appEndpoint(this.buildAppEndpoint(privDto.getAppEndpoint()))
                        .build()).collect(Collectors.toSet());
        
        AppSource savedAppSource = this.appSourceRepository.saveAndFlush(appSourceToSave);
        
        Iterable<AppEndpointPriv> existingPrivileges = this.appSourcePrivRepository.findAllByAppSource(appSourceToSave);
        this.appSourcePrivRepository.deleteAll(existingPrivileges);
        this.appSourcePrivRepository.saveAll(appEndpointPrivs);
        
        appSource.setId(savedAppSource.getId());

        return appSource;
    }
}
