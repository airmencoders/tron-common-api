package mil.tron.commonapi.service;

import mil.tron.commonapi.appgateway.AppSourceInterfaceDefinition;
import mil.tron.commonapi.dto.AppClientUserPrivDto;
import mil.tron.commonapi.dto.DashboardUserDto;
import mil.tron.commonapi.dto.PrivilegeDto;
import mil.tron.commonapi.dto.appsource.AppEndPointPrivDto;
import mil.tron.commonapi.dto.appsource.AppEndpointDto;
import mil.tron.commonapi.dto.appsource.AppSourceDetailsDto;
import mil.tron.commonapi.dto.appsource.AppSourceDto;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.appsource.AppEndpoint;
import mil.tron.commonapi.entity.appsource.AppEndpointPriv;
import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.exception.InvalidAppSourcePermissions;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.health.AppSourceHealthIndicator;
import mil.tron.commonapi.logging.CommonApiLogger;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import mil.tron.commonapi.repository.appsource.AppEndpointPrivRepository;
import mil.tron.commonapi.repository.appsource.AppEndpointRepository;
import mil.tron.commonapi.repository.appsource.AppSourceRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.assertj.core.util.Lists;
import org.assertj.core.util.Sets;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.util.UriUtils;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

@Service("appSourceService")
public class AppSourceServiceImpl implements AppSourceService {
    private final Log appSourceServiceLog = LogFactory.getLog(CommonApiLogger.class);

    private AppSourceRepository appSourceRepository;
    private AppEndpointPrivRepository appEndpointPrivRepository;
    private AppEndpointRepository appEndpointRepository;
    private AppClientUserRespository appClientUserRespository;
    private PrivilegeRepository privilegeRepository;
    private DashboardUserRepository dashboardUserRepository;
    private DashboardUserService dashboardUserService;
    private HealthContributorRegistry healthContributorRegistry;
    private AppGatewayService appGatewayService;
    private static final String APP_SOURCE_ADMIN_PRIV = "APP_SOURCE_ADMIN";
    private static final String APP_SOURCE_NOT_FOUND_MSG = "No App Source found with id %s.";
    private static final String APP_SOURCE_NO_ENDPOINT_FOUND_MSG = "No App Source Endpoint found with id %s.";
    private static final String APP_SOURCE_WITH_APP_ENDPOINT_NOT_FOUND_MSG = "No App Source found with App Endpoint that has id %s.";
    private static final String APP_CLIENT_NOT_FOUND_MSG = "No App Client found with id %s.";
    private static final String APP_SOURCE_HEALTH_PREFIX = "appsource_";
    private ModelMapper mapper = new ModelMapper();
    private static final String APP_API_SPEC_NOT_FOUND_MSG = "Could not find API Specification for App Source with id %s.";
    private String appSourceApiDefinitionsLocation;


    // Per Sonarqube documentation, this shouldn't even be flagged for S107. It is though, and we should ignore it.
    @java.lang.SuppressWarnings("squid:S00107")
    @Autowired
    public AppSourceServiceImpl(AppSourceRepository appSourceRepository,
                                AppEndpointPrivRepository appEndpointPrivRepository,
                                AppEndpointRepository appEndpointRepository,
                                AppClientUserRespository appClientUserRespository,
                                PrivilegeRepository privilegeRepository,
                                DashboardUserRepository dashboardUserRepository,
                                DashboardUserService dashboardUserService,
                                HealthContributorRegistry healthContributorRegistry,
                                AppGatewayService appGatewayService,
                                @Value("${appsource-definitions}") String appSourceApiDefinitionsLocation)
    {
        this.appSourceRepository = appSourceRepository;
        this.appEndpointPrivRepository = appEndpointPrivRepository;
        this.appEndpointRepository = appEndpointRepository;
        this.appClientUserRespository = appClientUserRespository;
        this.dashboardUserRepository = dashboardUserRepository;
        this.privilegeRepository = privilegeRepository;
        this.dashboardUserService = dashboardUserService;
        this.healthContributorRegistry = healthContributorRegistry;
        this.appSourceApiDefinitionsLocation = appSourceApiDefinitionsLocation;
        this.appGatewayService = appGatewayService;
    }

    /**
     * Launch the health check instances for each app source that's supposed to report status
     */
    @PostConstruct
    void init() {
        List<AppSource> appSources = appSourceRepository.findAll();
        for (AppSource appSource : appSources) {
            registerAppReporting(appSource);
        }
    }

    /**
     * Helper for the registerAppReporting method to concat URL paths, that one or both
     * may or may not have a trailing slash.  Just takes the base url from the app gateway
     * URL.
     * @param url base url
     * @param path path
     * @throws MalformedURLException
     * @return the concatenated URL string
     */
    private String concatPaths(String url, String path) throws MalformedURLException {

        String newUrl = (url == null ? "" : url);
        String newPath = (path == null ? "" : path);

        if (newUrl.endsWith("/")) {
            newUrl = newUrl.substring(0, newUrl.length() - 1);
        }
        if (newPath.startsWith("/")) {
            newPath = newPath.substring(1);
        }
        return "http://" + new URL(newUrl).getHost() + "/" + newPath;
    }

    /**
     * Method to register or deregister a given app source from Spring Actuator's Health registry
     * @param appSource the App Source entity
     */
    private void registerAppReporting(AppSource appSource) {

        // unregister the health check for this app source (idempotent call)
        healthContributorRegistry
                .unregisterContributor(APP_SOURCE_HEALTH_PREFIX + appSource.getName());

        if (appSource.isReportStatus() && appSource.isAvailableAsAppSource()) {
            Map<String, AppSourceInterfaceDefinition> defs = appGatewayService.getDefMap();

            // if this path is registered as an app source, and it has an entry in the gateway service,
            //  register it with the Spring Actuator's health registry
            if (defs.containsKey(appSource.getAppSourcePath())) {
                try {
                    healthContributorRegistry
                            .registerContributor(
                                    // ID the registry entry by the apps name property
                                    APP_SOURCE_HEALTH_PREFIX + appSource.getName(),
                                    new AppSourceHealthIndicator(
                                            APP_SOURCE_HEALTH_PREFIX + appSource.getName(),
                                            // build the health url from the url in the app source config file + path given in the db
                                            concatPaths(defs.get(appSource.getAppSourcePath()).getSourceUrl(), appSource.getHealthUrl())));
                }
                catch (IllegalStateException e) {
                    appSourceServiceLog.info("App Source Health Indicator already registered for: " + appSource.getName() + ": " + e.getMessage());
                }
                catch (MalformedURLException e) {
                    appSourceServiceLog.warn("Malformed Health URL for: " + appSource.getName() + ": " + e.getMessage());
                }

            }
        }
    }

    @Override
    public List<AppSourceDto> getAppSources() {
        Iterable<AppSource> appSources = this.appSourceRepository.findByAvailableAsAppSourceTrue();
        return StreamSupport
                .stream(appSources.spliterator(), false)
                .map(appSource -> AppSourceDto.builder().id(appSource.getId())
                        .name(appSource.getName())
                        .endpointCount(appSource.getAppEndpoints().size())
                        .clientCount(getAppSourceUniqueClientCount(appSource.getAppPrivs()))
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

        AppSource existingAppSource = this.appSourceRepository.findById(id).orElseThrow(() -> new RecordNotFoundException(String.format(APP_SOURCE_NOT_FOUND_MSG, id)));
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
                .orElseThrow(() -> new RecordNotFoundException(String.format(APP_SOURCE_NOT_FOUND_MSG, id)));

        // remove admins attached to this app
        toRemove = this.deleteAdminsFromAppSource(toRemove, "", true);

        // remove privileges associated with the app source
        this.appEndpointPrivRepository.removeAllByAppSource(AppSource.builder().id(id).build());
        // remove endpoints associated with the app source
        this.appEndpointRepository.removeAllByAppSource(AppSource.builder().id(id).build());
        
        // If this exists as an App Client, we should disable as an AppSource, else delete
        if(this.appClientUserRespository.existsByIdAndAvailableAsAppClientTrue(id)) {
            this.deleteAdminsFromAppSource(toRemove, "", true);

            toRemove.setOpenApiSpecFilename(null);
            toRemove.setAppSourcePath(null);
            toRemove.setAvailableAsAppSource(false);
            this.appSourceRepository.save(toRemove);
        } else{
            this.appSourceRepository.deleteById(toRemove.getId());
        }
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
    
    private int getAppSourceUniqueClientCount(Set<AppEndpointPriv> privileges) {
    	return privileges.stream().collect(groupingBy(AppEndpointPriv::getAppClientUser, counting())).size();
    }

    private AppSourceDetailsDto buildAppSourceDetailsDto(AppSource appSource) {
        return AppSourceDetailsDto.builder()
                .id(appSource.getId())
                .name(appSource.getName())
                .reportStatus(appSource.isReportStatus())
                .healthUrl(appSource.getHealthUrl())
                .endpoints(appSource.getAppEndpoints().stream()
                    .map(appEndpoint -> AppEndpointDto.builder()
                        .id(appEndpoint.getId())
                        .path(appEndpoint.getPath())
                        .requestType(appEndpoint.getMethod().toString())
                        .deleted(appEndpoint.isDeleted())
                        .build()).collect(Collectors.toList()))
                .endpointCount(appSource.getAppEndpoints().size())
                .appClients(appSource.getAppPrivs().stream()
                    .map(appEndpointPriv -> AppClientUserPrivDto.builder()
                        .id(appEndpointPriv.getId())
                        .appClientUser(appEndpointPriv.getAppClientUser().getId())
                        .appClientUserName(appEndpointPriv.getAppClientUser().getName())
                        .privilege(appEndpointPriv.getAppEndpoint().getPath())
                        .appEndpoint(appEndpointPriv.getAppEndpoint().getId())
                        .build()).collect(Collectors.toList()))
                .clientCount(getAppSourceUniqueClientCount(appSource.getAppPrivs()))
                .appSourceAdminUserEmails(appSource.getAppSourceAdmins().stream()
                        .map(DashboardUser::getEmail)
                        .collect(Collectors.toList()))
                .appSourcePath(appSource.getAppSourcePath())
                .build();
    }

    private AppSourceDetailsDto saveAppSource(UUID uuid, AppSourceDetailsDto appSource) {
        AppSource appSourceToSave = uuid != null ?
            this.appSourceRepository.findById(uuid).orElse(AppSource.builder().id(uuid).build()) :
            this.appSourceRepository.findByNameIgnoreCase(appSource.getName()).orElse(AppSource.builder().id(UUID.randomUUID()).build());

        appSourceToSave.setName(appSource.getName());
        appSourceToSave.setAvailableAsAppSource(true);
        appSourceToSave.setReportStatus(appSource.isReportStatus());

        // encode the given URL as a URI
        appSourceToSave
                .setHealthUrl(
                        UriUtils.encode(
                                appSource.getHealthUrl() != null ? appSource.getHealthUrl() : "", StandardCharsets.UTF_8));

        Set<AppEndpoint> appEndpoints = appSource.getEndpoints()
            .stream().map(endpointDto -> AppEndpoint.builder()
                .id(endpointDto.getId())
                .appSource(appSourceToSave)
                .method(RequestMethod.valueOf(endpointDto.getRequestType()))
                .path(endpointDto.getPath())
                .deleted(endpointDto.isDeleted())
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

        // setup any reporting this app source needs to do
        this.registerAppReporting(savedAppSource);
                  
        // Remove and reapply the Endpoints and Endpoint Privileges 
        // This allows us to change endpoints/privileges via PUT
        Iterable<AppEndpointPriv> existingPrivileges = this.appEndpointPrivRepository.findAllByAppSource(appSourceToSave);
        this.appEndpointPrivRepository.deleteAll(existingPrivileges);

        List<AppEndpoint> existingEndpoints = this.appEndpointRepository.findAllByAppSource(appSourceToSave);
        existingEndpoints.removeAll(appEndpoints);
        this.appEndpointRepository.deleteAll(existingEndpoints);

        this.appEndpointRepository.saveAll(appEndpoints);
        this.appEndpointPrivRepository.saveAll(appEndpointPrivs);
        
        appSource.setId(savedAppSource.getId());
        appSource.setClientCount(getAppSourceUniqueClientCount(savedAppSource.getAppPrivs()));
        appSource.setEndpointCount(appEndpoints.size());
        appSource.setAppSourcePath(savedAppSource.getAppSourcePath());

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
                            .privileges(Lists.newArrayList(mapper.map(appSourcePriv, PrivilegeDto.class)))
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
                .orElseThrow(() -> new RecordNotFoundException(String.format(APP_SOURCE_NOT_FOUND_MSG, appSourceId)));

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
                .orElseThrow(() -> new RecordNotFoundException(String.format(APP_SOURCE_NOT_FOUND_MSG, appId)));

        return appSourceContainsAdminEmail(appSource, email);
    }
    
    /**
     * Check if the given email address is APP_SOURCE_ADMIN for given endpoint UUID
     * @param endpointId UUID of the endpoint
     * @param email email of user
     * @return true if user is an assigned admin for the app source of the given endpoint
     */
    @Override
    public boolean userIsAdminForAppSourceByEndpoint(UUID endpointId, String email) {
    	AppEndpoint appEndpoint = appEndpointRepository.findById(endpointId)
    			.orElseThrow(() -> new RecordNotFoundException(String.format("App Endpoint with id %s not found", endpointId.toString())));
    	AppSource appSource = appEndpoint.getAppSource();
    	
        return appSourceContainsAdminEmail(appSource, email);
    }
    
    /**
     * Check if the App Source has an Admin with the given email
     * @param appSource the App Source to check
     * @param email the email to check against
     * @return true if App Source has an admin with the given email, false otherwise
     */
    private boolean appSourceContainsAdminEmail(AppSource appSource, String email) {
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
                .orElseThrow(() -> new RecordNotFoundException(String.format(APP_SOURCE_NOT_FOUND_MSG, appSourceId)));

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
     * and dashboard user privs in it... used for removing Dashboard user from an AppSource
     * @param privs set of privileges to examine
     * @return if there's other privileges than App Source Admin
     */
    private boolean privSetHasPrivsOtherThanAppSource(Set<Privilege> privs) {
        for (Privilege priv : privs) {
            if (!priv.getName().equals(APP_SOURCE_ADMIN_PRIV) &&
                !priv.getName().equals("DASHBOARD_USER")) return true;
        }
        return false;
    }


    /**
     * Deletes all app clients privileges for this app source - one shot to shut down all
     *   app client accesses to app source endpoints
     * @param appSourceId UUID of the app source to remove all app client privileges from
     * @return the modified app source details dto
     */
    @Override
    public AppSourceDetailsDto deleteAllAppClientPrivs(UUID appSourceId) {
        AppSource appSource = this.appSourceRepository.findById(appSourceId)
                .orElseThrow(() -> new RecordNotFoundException(String.format(APP_SOURCE_NOT_FOUND_MSG, appSourceId)));

        List<AppEndpointPriv> appClientPrivs = new ArrayList<>(appSource.getAppPrivs());

        appSource.setAppPrivs(new HashSet<>());
        for (AppEndpointPriv priv : new ArrayList<>(appClientPrivs)) {
            appEndpointPrivRepository.delete(priv);
        }

        appSourceRepository.saveAndFlush(appSource);
        return this.buildAppSourceDetailsDto(appSource);
    }

    /**
     * Adds an app source endpoint privilege relationship to some app client
     * @param dto the AppEndPointPrivDto containing the app source, app client, and app source endpoint's UUIDs
     * @return the modified AppSourceDetailsDto or else throws if that app source to app client to endpoint priv exists
     */
    @Override
    public AppSourceDetailsDto addEndPointPrivilege(AppEndPointPrivDto dto) {
        AppSource appSource = this.appSourceRepository.findById(dto.getAppSourceId())
                .orElseThrow(() -> new RecordNotFoundException(String.format(APP_SOURCE_NOT_FOUND_MSG, dto.getAppSourceId())));

        AppEndpoint endPoint = this.appEndpointRepository.findById(dto.getAppEndpointId())
                .orElseThrow(() -> new RecordNotFoundException(String.format(APP_SOURCE_NO_ENDPOINT_FOUND_MSG, dto.getAppEndpointId())));

        AppClientUser appClient = this.appClientUserRespository.findById(dto.getAppClientUserId())
                .orElseThrow(() -> new RecordNotFoundException(String.format(APP_CLIENT_NOT_FOUND_MSG, dto.getAppClientUserId())));

        if (!appEndpointPrivRepository
                .existsByAppSourceEqualsAndAppClientUserEqualsAndAppEndpointEquals(appSource, appClient, endPoint)) {

            AppEndpointPriv newPriv = AppEndpointPriv.builder()
                    .id(UUID.randomUUID())
                    .appSource(appSource)
                    .appClientUser(appClient)
                    .appEndpoint(endPoint)
                    .build();

            appEndpointPrivRepository.saveAndFlush(newPriv);

            Set<AppEndpointPriv> privs = new HashSet<>(appSource.getAppPrivs());
            privs.add(newPriv);
            appSource.setAppPrivs(privs);

            return this.buildAppSourceDetailsDto(appSourceRepository.saveAndFlush(appSource));
        }
        else {
            throw new ResourceAlreadyExistsException("An App Source to App Client privilege already exists for that endpoint");
        }
    }

    /**
     * Removes (deletes) a single app source endpoint to app client privilege from the provided app source
     * @param appSourceId the UUID of the app source
     * @param appSourceEndPointPrivId the UUID of the app source endpoint priv to delete
     * @return the modified AppSourceDetailsDto or else throws if that app source to app client to endpoint priv exists
     */
    @Override
    public AppSourceDetailsDto removeEndPointPrivilege(UUID appSourceId, UUID appSourceEndPointPrivId) {
        AppSource appSource = this.appSourceRepository.findById(appSourceId)
                .orElseThrow(() -> new RecordNotFoundException(String.format(APP_SOURCE_NOT_FOUND_MSG, appSourceId)));

        AppEndpointPriv endPoint = this.appEndpointPrivRepository.findById(appSourceEndPointPrivId)
                .orElseThrow(() -> new RecordNotFoundException(String.format(APP_SOURCE_NO_ENDPOINT_FOUND_MSG, appSourceEndPointPrivId)));

        // verify this endpoint is part of this app source... and not some other app source
        if (appSource.getId().equals(endPoint.getAppSource().getId())) {
            appEndpointPrivRepository.deleteById(appSourceEndPointPrivId);

            Set<AppEndpointPriv> privs = new HashSet<>(appSource.getAppPrivs());
            privs.removeIf(item -> item.getId().equals(appSourceEndPointPrivId));
            appSource.setAppPrivs(privs);

            // return the new app source record
            return this.buildAppSourceDetailsDto(appSourceRepository.saveAndFlush(appSource));
        }
        else {
            throw new InvalidAppSourcePermissions(String.format("Endpoint privilege with ID %s does not belong to app source %s", appSourceEndPointPrivId, appSourceId));
        }
    }

    /**
     * Deletes an admin with given DashboardUser from all app sources he/she may be an admin of
     * @param user DashboardUser to search and delete from app source(s)
     */
    @Override
    public void deleteAdminFromAllAppSources(DashboardUser user) {
        List<AppSource> usersAppSources = appSourceRepository.findAppSourcesByAppSourceAdminsContaining(user);
        for (AppSource appSource : usersAppSources) {
            appSource.getAppSourceAdmins().remove(user);
            appSourceRepository.saveAndFlush(appSource);
        }
    }

    /**
     * Searches AppSourceDefs to find open API spec file, returns file if found
     * @param id App Source Id to get API specification for
     * @return Resource that is guaranteed to exist
     */
    public Resource getApiSpecForAppSource(UUID id) {
        AppSource appSource = appSourceRepository.findById(id).orElseThrow(() -> new RecordNotFoundException(String.format(APP_SOURCE_NOT_FOUND_MSG, id)));
        return getResource(id, appSource.getOpenApiSpecFilename());
    }

    /**
     * Searches AppSourceDefs to find open API spec file, returns file if found
     * @param id App Endpoint Id to get parent App Source's API specification for
     * @return Resource that is guaranteed to exist
     */
    public Resource getApiSpecForAppSourceByEndpointPriv(UUID id) {
        AppSource appSource = appSourceRepository.findByAppPrivs_Id(id).orElseThrow(() -> new RecordNotFoundException(String.format(APP_SOURCE_WITH_APP_ENDPOINT_NOT_FOUND_MSG, id)));
        return getResource(id, appSource.getOpenApiSpecFilename());
    }

    private Resource getResource(UUID id, String filename) {
        Resource resource = new ClassPathResource(appSourceApiDefinitionsLocation +  filename);
        if(resource.exists()) {
            return resource;
        } else {
            throw new RecordNotFoundException(String.format(APP_API_SPEC_NOT_FOUND_MSG, id));
        }
    }
}
