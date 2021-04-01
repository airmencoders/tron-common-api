package mil.tron.commonapi.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.transaction.Transactional;

import org.assertj.core.util.Lists;
import org.assertj.core.util.Sets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMethod;

import mil.tron.commonapi.dto.AppClientUserPrivDto;
import mil.tron.commonapi.dto.DashboardUserDto;
import mil.tron.commonapi.dto.appsource.AppEndpointDto;
import mil.tron.commonapi.dto.appsource.AppSourceDetailsDto;
import mil.tron.commonapi.dto.appsource.AppSourceDto;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.appsource.AppEndpoint;
import mil.tron.commonapi.entity.appsource.AppEndpointPriv;
import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import mil.tron.commonapi.repository.appsource.AppEndpointPrivRepository;
import mil.tron.commonapi.repository.appsource.AppEndpointRepository;
import mil.tron.commonapi.repository.appsource.AppSourceRepository;

@Service
public class AppSourceServiceImpl implements AppSourceService {

    private AppSourceRepository appSourceRepository;
    private AppEndpointPrivRepository appEndpointPrivRepository;
    private AppEndpointRepository appEndpointRepository;
    private AppClientUserRespository appClientUserRespository;
    private PrivilegeRepository privilegeRepository;
    private DashboardUserRepository dashboardUserRepository;
    private DashboardUserService dashboardUserService;
    private static final String APP_SOURCE_ADMIN_PRIV = "APP_SOURCE_ADMIN";
    private static final String APP_SOURCE_NO_FOUND_MSG = "No App Source found with id %s.";

    @Autowired
    public AppSourceServiceImpl(AppSourceRepository appSourceRepository,
                                AppEndpointPrivRepository appEndpointPrivRepository,
                                AppEndpointRepository appEndpointRepository,
                                AppClientUserRespository appClientUserRespository,
                                PrivilegeRepository privilegeRepository,
                                DashboardUserRepository dashboardUserRepository,
                                DashboardUserService dashboardUserService) {
        this.appSourceRepository = appSourceRepository;
        this.appEndpointPrivRepository = appEndpointPrivRepository;
        this.appEndpointRepository = appEndpointRepository;
        this.appClientUserRespository = appClientUserRespository;
        this.dashboardUserRepository = dashboardUserRepository;
        this.privilegeRepository = privilegeRepository;
        this.dashboardUserService = dashboardUserService;
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

    @Override
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
        return this.buildAppSourceDetailsDto(appSource);
    }

    @Override
    public AppSourceDetailsDto updateAppSource(UUID id, AppSourceDetailsDto appSourceDetailsDto) {
        // validate id
        if (!id.equals(appSourceDetailsDto.getId())) {
            throw new InvalidRecordUpdateRequest(String.format("ID: %s does not match the resource ID: %s",
                    id, appSourceDetailsDto.getId()));
        }

        AppSource existingAppSource = this.appSourceRepository.findById(id).orElseThrow(() -> new RecordNotFoundException(String.format(APP_SOURCE_NO_FOUND_MSG, id)));
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
                .orElseThrow(() -> new RecordNotFoundException(String.format(APP_SOURCE_NO_FOUND_MSG, id)));

        // remove admins attached to this app
        toRemove = this.deleteAdminsFromAppSource(toRemove, "", true);

        // remove privileges associated with the app source
        this.appEndpointPrivRepository.removeAllByAppSource(AppSource.builder().id(id).build());
        // remove endpoints associated with the app source
        this.appEndpointRepository.removeAllByAppSource(AppSource.builder().id(id).build());
        this.appSourceRepository.deleteById(toRemove.getId());
        return this.buildAppSourceDetailsDto(toRemove);
    }

    private AppClientUser buildAppClientUser(UUID appClientId) throws RecordNotFoundException {
        AppClientUser appClientUser = this.appClientUserRespository.findById(appClientId)
        		.orElseThrow(() -> new RecordNotFoundException(String.format("No app client with id %s found.", appClientId)));
        
        return AppClientUser.builder().id(appClientUser.getId()).name(appClientUser.getName()).build();
    }

    private AppEndpoint findAppEndpoint(UUID appEndpointId, Collection<AppEndpoint> appEndpoints) throws RecordNotFoundException {
        return appEndpoints.stream()
            .filter(endpoint -> appEndpointId.equals(endpoint.getId()))
            .findAny()
            .orElseThrow(() ->new RecordNotFoundException(String.format("No app endpoint with id %s found.", appEndpointId)));
    }

    private AppSourceDetailsDto buildAppSourceDetailsDto(AppSource appSource) {
        return AppSourceDetailsDto.builder()
                .id(appSource.getId())
                .name(appSource.getName())
                .endpoints(appSource.getAppEndpoints().stream()
                    .map(appEndpoint -> AppEndpointDto.builder()
                        .id(appEndpoint.getId())
                        .path(appEndpoint.getPath())
                        .requestType(appEndpoint.getMethod().toString())
                        .build()).collect(Collectors.toList()))
                .appClients(appSource.getAppPrivs().stream()
                    .map(appEndpointPriv -> AppClientUserPrivDto.builder()
                        .appClientUser(appEndpointPriv.getAppClientUser().getId())
                        .appClientUserName(appEndpointPriv.getAppClientUser().getName())
                        .privilege(appEndpointPriv.getAppEndpoint().getPath())
                        .appEndpoint(appEndpointPriv.getAppEndpoint().getId())
                        .build()).collect(Collectors.toList()))
                .appSourceAdminUserEmails(appSource.getAppSourceAdmins().stream()
                        .map(DashboardUser::getEmail)
                        .collect(Collectors.toList()))
                .build();
    }

    private AppSourceDetailsDto saveAppSource(UUID uuid, AppSourceDetailsDto appSource) {
        AppSource appSourceToSave = uuid != null ?
            this.appSourceRepository.findById(uuid).orElse(AppSource.builder().id(uuid).build()) :
            AppSource.builder().id(UUID.randomUUID()).build();

        appSourceToSave.setName(appSource.getName());

        Set<AppEndpoint> appEndpoints = appSource.getEndpoints()
            .stream().map(endpointDto -> AppEndpoint.builder()
                .id(endpointDto.getId())
                .appSource(appSourceToSave)
                .method(RequestMethod.valueOf(endpointDto.getRequestType()))
                .path(endpointDto.getPath())
                .build()).collect(Collectors.toSet());
        
        Set<AppEndpointPriv> appEndpointPrivs = appSource.getAppClients()
            .stream().map(privDto -> AppEndpointPriv.builder()
                .appSource(appSourceToSave)
                .appClientUser(this.buildAppClientUser(privDto.getAppClientUser()))
                .appEndpoint(privDto.getAppEndpoint() == null ? null : this.findAppEndpoint(privDto.getAppEndpoint(), appEndpoints))
                .build()).collect(Collectors.toSet());

        // remove admins attached to this app, essentially sanitize admins from it
        //  this allows us to essentially be able to add and delete admins via an HTTP PUT
        AppSource appSourceCleanAdmins = this.deleteAdminsFromAppSource(appSourceToSave, "", true);

        // resolve the app admin's from their DTO emails, create/add if needed
        List<DashboardUser> thisAppsAdminUsers = new ArrayList<>();
        for (String email : appSource.getAppSourceAdminUserEmails()) {
            thisAppsAdminUsers.add(this.createDashboardUserWithAsAppSourceAdmin(email));
        }

        // re-apply the admins received in the DTO
        appSourceCleanAdmins.setAppSourceAdmins(Sets.newHashSet(thisAppsAdminUsers));

        // persist the app source and its changes
        AppSource savedAppSource = this.appSourceRepository.saveAndFlush(appSourceCleanAdmins);
                  
        // Remove and reapply the Endpoints and Endpoint Privileges 
        // This allows us to change endpoints/privileges via PUT
        Iterable<AppEndpointPriv> existingPrivileges = this.appEndpointPrivRepository.findAllByAppSource(appSourceToSave);
        this.appEndpointPrivRepository.deleteAll(existingPrivileges);

        Iterable<AppEndpoint> existingEndpoints = this.appEndpointRepository.findAllByAppSource(appSourceToSave);
        this.appEndpointRepository.deleteAll(existingEndpoints);

        this.appEndpointRepository.saveAll(appEndpoints);
        this.appEndpointPrivRepository.saveAll(appEndpointPrivs);
        
        appSource.setId(savedAppSource.getId());

        return appSource;
    }

    /**
     * Private helper to make a dashboard user with given email as an app source admin,
     * or if that email is already a dashboard user, just adds app source admin to the set
     * of privileges
     * @param email the user email
     * @return the newly created or modified dashboard user record
     */
    private DashboardUser createDashboardUserWithAsAppSourceAdmin(String email) {
        Privilege appSourcePriv = privilegeRepository.findByName(APP_SOURCE_ADMIN_PRIV)
                .orElseThrow(() -> new RecordNotFoundException("Cannot find APP SOURCE ADMIN privilege"));

        Optional<DashboardUser> user = dashboardUserRepository.findByEmailIgnoreCase(email);
        if (user.isEmpty()) {
            return dashboardUserService.convertToEntity(dashboardUserService
                    .createDashboardUserDto(DashboardUserDto
                            .builder()
                            .id(UUID.randomUUID())
                            .email(email)
                            .privileges(Lists.newArrayList(appSourcePriv))
                            .build()));
        }
        else {
            DashboardUser existingUser = user.get();
            Set<Privilege> newPrivs = new HashSet<>(existingUser.getPrivileges());
            newPrivs.add(appSourcePriv);
            existingUser.setPrivileges(newPrivs);
            return existingUser;
        }
    }

    /**
     * Adds a user to this app's set of admins
     * @param appSourceId app source's id
     * @param email the user's email
     */
    @Override
    public AppSourceDetailsDto addAppSourceAdmin(UUID appSourceId, String email) {
        AppSource appSource = this.appSourceRepository.findById(appSourceId)
                .orElseThrow(() -> new RecordNotFoundException(String.format(APP_SOURCE_NO_FOUND_MSG, appSourceId)));

        appSource.getAppSourceAdmins().add(createDashboardUserWithAsAppSourceAdmin(email));
        return this.buildAppSourceDetailsDto(appSourceRepository.saveAndFlush(appSource));
    }

    /**
     * Check if the given email address is APP_SOURCE_ADMIN for given app source UUID
     * @param appId UUID of the app source
     * @param email email of user
     * @return true if user is an assigned admin for the app source
     */
    @Override
    public boolean userIsAdminForAppSource(UUID appId, String email) {
        AppSource appSource = this.appSourceRepository.findById(appId)
                .orElseThrow(() -> new RecordNotFoundException(String.format(APP_SOURCE_NO_FOUND_MSG, appId)));

        for (DashboardUser user : appSource.getAppSourceAdmins()) {
            if (user.getEmail().equalsIgnoreCase(email)) {
                for (Privilege priv : user.getPrivileges()) {
                    if (priv.getName().equalsIgnoreCase(APP_SOURCE_ADMIN_PRIV)) return true;
                }
            }
        }
        return false;
    }

    /**
     * Removes a dashboard user from this app's set of admins
     * If the dashboard user is not associated with any other apps or have any other privs
     * (e.g. DASHBOARD_ADMIN, etc), then that record is deleted completely, otherwise
     * they are just removed from the given app
     * @param appSourceId the app source's id
     * @param email the user's email
     */
    @Override
    public AppSourceDetailsDto removeAdminFromAppSource(UUID appSourceId, String email) {
        AppSource appSource = this.appSourceRepository.findById(appSourceId)
                .orElseThrow(() -> new RecordNotFoundException(String.format(APP_SOURCE_NO_FOUND_MSG, appSourceId)));

        appSource = this.deleteAdminsFromAppSource(appSource, email, false);

        return this.buildAppSourceDetailsDto(appSource);
    }

    /**
     * Private helper that removes one or all admins from an app source
     * @param appSource the app source entity to modify
     * @param email email of the admin to remove
     * @param deleteAll whether to delete all admins (if true negates the email parameter)
     * @return the modified app source entity
     */
    private AppSource deleteAdminsFromAppSource(AppSource appSource, String email, boolean deleteAll) {
        if (appSource.getAppSourceAdmins() == null) return appSource;

        for (DashboardUser user : new HashSet<>(appSource.getAppSourceAdmins())) {
            if (!deleteAll && !user.getEmail().equalsIgnoreCase(email)) continue;

            // at the minimum we remove this user from this app source
            Set<DashboardUser> admins = new HashSet<>(appSource.getAppSourceAdmins());
            admins.remove(user);
            appSource.setAppSourceAdmins(admins);
            appSource = appSourceRepository.saveAndFlush(appSource);

            List<AppSource> usersAppSources = appSourceRepository.findAppSourcesByAppSourceAdminsContaining(user);
            if (usersAppSources.isEmpty() && !privSetHasPrivsOtherThanAppSource(user.getPrivileges())) {

                // this user doesn't belong to other apps and has no other privileges
                //   so delete completely
                dashboardUserService.deleteDashboardUser(user.getId());
            }
            else if (usersAppSources.isEmpty() && privSetHasPrivsOtherThanAppSource(user.getPrivileges())) {

                // this user doesn't belong to other app source apps but HAS other privileges
                //   in the system, so just remove the APP_SOURCE_ADMIN priv since keeping it
                //   would make it an "orphaned" and unnecessary privilege
                Set<Privilege> userPrivs = new HashSet<>(user.getPrivileges());
                userPrivs.removeIf(p -> p.getName().equalsIgnoreCase(APP_SOURCE_ADMIN_PRIV));
                user.setPrivileges(userPrivs);
                dashboardUserRepository.save(user);
            }
        }

        return appSource;
    }

    /**
     * Private helper to examine a set of privileges to see if there's other-than-app-source-admin
     * priv in it... used for removing Dashboard user from an AppSource
     * @param privs set of privileges to examine
     * @return if there's other privileges than App Source Admin
     */
    private boolean privSetHasPrivsOtherThanAppSource(Set<Privilege> privs) {
        for (Privilege priv : privs) {
            if (!priv.getName().equals(APP_SOURCE_ADMIN_PRIV)) return true;
        }
        return false;
    }


}