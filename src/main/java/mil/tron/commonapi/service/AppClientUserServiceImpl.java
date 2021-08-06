package mil.tron.commonapi.service;

import mil.tron.commonapi.dto.DashboardUserDto;
import mil.tron.commonapi.dto.PrivilegeDto;
import mil.tron.commonapi.dto.appclient.AppClientSummaryDto;
import mil.tron.commonapi.dto.appclient.AppClientUserDetailsDto;
import mil.tron.commonapi.dto.appclient.AppClientUserDto;
import mil.tron.commonapi.dto.appclient.AppEndpointClientInfoDto;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.dto.pubsub.SubscriberDto;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.appsource.AppEndpointPriv;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import mil.tron.commonapi.repository.appsource.AppEndpointPrivRepository;
import mil.tron.commonapi.repository.appsource.AppSourceRepository;
import mil.tron.commonapi.service.pubsub.SubscriberService;
import mil.tron.commonapi.service.pubsub.SubscriberServiceImpl;
import org.assertj.core.util.Lists;
import org.assertj.core.util.Sets;
import org.modelmapper.Converter;
import org.modelmapper.ModelMapper;
import org.modelmapper.spi.MappingContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class AppClientUserServiceImpl implements AppClientUserService {
	private static final DtoMapper MODEL_MAPPER = new DtoMapper();
	private static final String APP_CLIENT_NOT_FOUND_MSG = "No App Client found with id %s.";
	private static final String APP_CLIENT_DEVELOPER_PRIV = "APP_CLIENT_DEVELOPER";

	private AppClientUserRespository appClientRepository;
	private AppSourceRepository appSourceRepository;
	private DashboardUserService dashboardUserService;
	private PrivilegeRepository privilegeRepository;
	private DashboardUserRepository dashboardUserRepository;
	private AppEndpointPrivRepository appEndpointPrivRepository;
	private SubscriberService subscriberService;
	private ModelMapper mapper = new ModelMapper();

	@Value("${api-prefix.v1}")
	private String apiPrefix;

	@Value("${app-sources-prefix}")
	private String appSourcePrefix;

	public AppClientUserServiceImpl(AppClientUserRespository appClientRepository,
									AppSourceRepository appSourceRepository,
									DashboardUserService dashboardUserService,
									DashboardUserRepository dashboardUserRepository,
									PrivilegeRepository privilegeRepository,
									AppEndpointPrivRepository appEndpointPrivRepository,
									SubscriberService subscriberService) {

		this.appClientRepository = appClientRepository;
		this.appSourceRepository = appSourceRepository;
		this.dashboardUserService = dashboardUserService;
		this.dashboardUserRepository = dashboardUserRepository;
		this.privilegeRepository = privilegeRepository;
		this.appEndpointPrivRepository = appEndpointPrivRepository;
		this.subscriberService = subscriberService;

		Converter<List<Privilege>, Set<Privilege>> convertPrivilegesToSet = 
				((MappingContext<List<Privilege>, Set<Privilege>> context) -> new HashSet<>(context.getSource()));
		
		Converter<Set<Privilege>, List<Privilege>> convertPrivilegesToArr = 
				((MappingContext<Set<Privilege>, List<Privilege>> context) -> new ArrayList<>(context.getSource()));
		
		MODEL_MAPPER.addConverter(convertPrivilegesToSet);
		MODEL_MAPPER.addConverter(convertPrivilegesToArr);

	}
	
	@Override
	public Iterable<AppClientUserDto> getAppClientUsers() {
		return StreamSupport.stream(appClientRepository
				.findByAvailableAsAppClientTrue().spliterator(), false)
				.map(this::convertToDto)
				.collect(Collectors.toList());
	}

	@Override
	public AppClientUserDetailsDto getAppClient(UUID id) {
		AppClientUser client = appClientRepository.findById(id)
				.orElseThrow(() -> new RecordNotFoundException("Client App with that ID does not exist!"));

		return AppClientUserDetailsDto.builder()
				.id(client.getId())
				.name(client.getName())
				.appClientDeveloperEmails(Lists.newArrayList(client.getAppClientDevelopers())
						.stream()
						.map(DashboardUser::getEmail)
						.collect(Collectors.toList()))
				.privileges(Lists.newArrayList(client
						.getPrivileges()
						.stream()
						.map(item -> mapper.map(item, PrivilegeDto.class))
						.collect(Collectors.toList())))
				.appEndpointPrivs(client.getAppEndpointPrivs()
						.stream()
						.map(item -> AppEndpointClientInfoDto.builder()
							.appSourceName(item.getAppSource().getName())
							.path(item.getAppEndpoint().getPath())
							.method(item.getAppEndpoint().getMethod())
							.deleted(item.getAppEndpoint().isDeleted())
							.id(item.getId())
							.basePath(generateAppSourceBasePath(item.getAppSource().getAppSourcePath()))
							.appSourceId(item.getAppSource().getId())
							.build())
						.collect(Collectors.toList()))
				.clusterUrl(client.getClusterUrl())
				.build();
	}
	
	private String generateAppSourceBasePath(String appSourcePath) {
		return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(apiPrefix + "/")
                .path(appSourcePrefix + "/")
                .path(appSourcePath)
                .toUriString();
	}

	/**
	 * Gets just the names and UUIDs of the available app clients
	 * @return list of AppClientSummaryDtos
	 */
	@Override
	public Iterable<AppClientSummaryDto> getAppClientUserSummaries() {
		return StreamSupport
				.stream(appClientRepository.findByAvailableAsAppClientTrue().spliterator(), false)
				.map(item -> MODEL_MAPPER.map(item, AppClientSummaryDto.class))
				.collect(Collectors.toList());
	}
	
	@Override
	public AppClientUserDto createAppClientUser(AppClientUserDto appClient) {
		AppClientUser newUser = appClientRepository
			.findByNameIgnoreCase(appClient.getName()).orElse(null);
		// Either it doesn't exist, or the App technically exists, but not as an App Client
		if (newUser != null) {
			if (newUser.isAvailableAsAppClient()) {
				throw new ResourceAlreadyExistsException(String.format("Client Name: %s already exists", appClient.getName()));
			} else {
				appClient.setId(newUser.getId());
				return updateAppClientUser(newUser.getId(), appClient);
			}
		} else {
			AppClientUserDto sanitizedAppClientUserDto = sanitizeAppClientUserDto(appClient);
			
			newUser = AppClientUser.builder()
				.availableAsAppClient(true)
				.name(sanitizedAppClientUserDto.getName())
				.clusterUrl(sanitizedAppClientUserDto.getClusterUrl())
				.privileges(new HashSet<>(sanitizedAppClientUserDto
					.getPrivileges()
					.stream()
					.map(item -> mapper.map(item, Privilege.class))
					.collect(Collectors.toList())))
					.build();
			return convertToDto(appClientRepository.saveAndFlush(cleanAndResetDevs(newUser, sanitizedAppClientUserDto)));
		}	
	}

	@Override
	public AppClientUserDto updateAppClientUser(UUID id, AppClientUserDto appClient) {
		if (!id.equals(appClient.getId()))
			throw new InvalidRecordUpdateRequest(String.format("ID: %s does not match the resource ID: %s", id, appClient.getId()));
		
		AppClientUser dbUser = appClientRepository.findById(id).orElseThrow(() ->
				new RecordNotFoundException("Resource with the ID: " + id + " does not exist."));

		// Set the name if not subject to be changed
		if (appClient.getName() == null) {
			appClient.setName(dbUser.getName());
		}

		// Check for name uniqueness
		if (!isNameUnique(appClient, dbUser)) {
			throw new InvalidRecordUpdateRequest(String.format("Client Name: %s is already in use.", appClient.getName()));
		}
		
		AppClientUserDto sanitizedAppClientUserDto = sanitizeAppClientUserDto(appClient);
		
		dbUser.setName(sanitizedAppClientUserDto.getName());
		dbUser.setPrivileges(new HashSet<>(sanitizedAppClientUserDto.getPrivileges()
				.stream()
				.map(item -> mapper.map(item, Privilege.class))
				.collect(Collectors.toList())));

		dbUser.setClusterUrl(sanitizedAppClientUserDto.getClusterUrl());
		dbUser.setAvailableAsAppClient(true);

		// save/update and return
		return convertToDto(appClientRepository.saveAndFlush(cleanAndResetDevs(dbUser, sanitizedAppClientUserDto)));
	}

	/**
	 * Updates the app client developer specific parts of this app client record - managing other app client
	 * developers.  This is akin to a partial record update - constrained to the parts only an app client developer can
	 * change.
	 * @param id uuid of the app client
	 * @param appClient the app client dto sent from the controller
	 * @return the modified app client record
	 */
	@Override
	public AppClientUserDto updateAppClientDeveloperItems(UUID id, AppClientUserDto appClient) {
		if (!id.equals(appClient.getId()))
			throw new InvalidRecordUpdateRequest(String.format("ID: %s does not match the resource ID: %s", id, appClient.getId()));

		AppClientUser dbUser = appClientRepository
				.findById(id)
				.orElseThrow(() -> new RecordNotFoundException("Resource with the ID: " + id + " does not exist."));

		// save and return
		return convertToDto(appClientRepository.saveAndFlush(cleanAndResetDevs(dbUser, appClient)));
	}

	/**
	 * Private helper to get the ability to use the update methods to add/remove developers - which is
	 * done by removing them all from an entity and then adding the ones given from the request's dto
	 * @param entity the entity to modify
	 * @param appClientDto the dto from which to pull the list of devs
	 * @return the modified entity (not persisted to the db though)
	 */
	private AppClientUser cleanAndResetDevs(AppClientUser entity, AppClientUserDto appClientDto) {

		// remove developers attached to this app, essentially sanitize developers from it
		//  this allows us to essentially be able to add and delete developers via an HTTP PUT
		this.deleteDevelopersFromAppClient(entity, "", true);

		// resolve the app developer's from their DTO emails, create/add if needed
		List<DashboardUser> thisClientAppDevelopers = new ArrayList<>();

		if (appClientDto.getAppClientDeveloperEmails() == null) {
			entity.setAppClientDevelopers(new HashSet<>());
			return entity;
		}

		for (String email : appClientDto.getAppClientDeveloperEmails()) {
			thisClientAppDevelopers.add(this.createDashboardUserWithAsAppClientDeveloper(email));
		}

		// re-apply the developers received in the DTO
		entity.setAppClientDevelopers(Sets.newHashSet(thisClientAppDevelopers));

		// return modified entity
		return entity;
	}

	@Override
    public AppClientUserDto deleteAppClientUser(UUID id) {
		AppClientUser dbUser = appClientRepository
				.findById(id)
				.orElseThrow(() -> new RecordNotFoundException("Record with ID: " + id.toString() + " not found."));

		// remove developers attached to this app
		this.deleteDevelopersFromAppClient(dbUser, "", true);

		// remove any app endpoints too
		for (AppEndpointPriv priv : new HashSet<>(dbUser.getAppEndpointPrivs())) {
			appEndpointPrivRepository.delete(priv);
		}

		// delete any pub-sub subscriptions
		subscriberService.cancelSubscriptionsByAppClient(dbUser);

		AppClientUserDto dto = convertToDto(dbUser);
		// If this exists as an App Source, we should disable as an App Client, else delete
		if (appSourceRepository.existsByIdAndAvailableAsAppSourceTrue(id)) {			
			dbUser.setAvailableAsAppClient(false);
			dbUser.setClusterUrl(null);
			dbUser.setAppEndpointPrivs(null);
			dbUser.setAppClientDevelopers(null);
			dbUser.setPrivileges(null);
			appClientRepository.saveAndFlush(dbUser);
		} else {
			appClientRepository.deleteById(id);
		}

    	return dto;
    }

	private boolean isNameUnique(AppClientUserDto appClient, AppClientUser dbUser) {
		return appClient.getName().equalsIgnoreCase(dbUser.getName())
				|| appClientRepository.findByNameIgnoreCase(appClient.getName()).isEmpty();
	}

	private AppClientUserDto convertToDto(AppClientUser user) {
		AppClientUserDto dto = MODEL_MAPPER.map(user, AppClientUserDto.class);

		if (user.getAppClientDevelopers() == null) {
			dto.setAppClientDeveloperEmails(new ArrayList<>());
		}
		else {
			dto.setAppClientDeveloperEmails(user
					.getAppClientDevelopers()
					.stream()
					.map(DashboardUser::getEmail)
					.collect(Collectors.toList()));
		}

		return dto;
	}


	/**
	 * Private helper to make a dashboard user with given email as an app client developer,
	 * or if that email is already a dashboard user, just adds app client developer to the set
	 * of privileges
	 * @param email the user email
	 * @return the newly created or modified dashboard user record
	 */
	private DashboardUser createDashboardUserWithAsAppClientDeveloper(String email) {
		Privilege appClientPriv = privilegeRepository.findByName(APP_CLIENT_DEVELOPER_PRIV)
				.orElseThrow(() -> new RecordNotFoundException("Cannot find APP CLIENT DEVELOPER privilege"));

		Optional<DashboardUser> user = dashboardUserRepository.findByEmailIgnoreCase(email);
		if (user.isEmpty()) {
			return dashboardUserService.convertToEntity(dashboardUserService
					.createDashboardUserDto(DashboardUserDto
							.builder()
							.id(UUID.randomUUID())
							.email(email)
							.privileges(Lists.newArrayList(mapper.map(appClientPriv, PrivilegeDto.class)))
							.build()));
		}
		else {
			DashboardUser existingUser = user.get();
			Set<Privilege> newPrivs = new HashSet<>(existingUser.getPrivileges());
			newPrivs.add(appClientPriv);
			existingUser.setPrivileges(newPrivs);
			return existingUser;
		}
	}

	/**
	 * Check if the given email address is APP_CLIENT_DEVELOPER for given app source UUID
	 * @param appId UUID of the app client
	 * @param email email of user
	 * @return true if user is an assigned developer for the app client
	 */
	@Override
	public boolean userIsAppClientDeveloperForApp(UUID appId, String email) {
		AppClientUser appClient = this.appClientRepository.findById(appId)
				.orElseThrow(() -> new RecordNotFoundException(String.format(APP_CLIENT_NOT_FOUND_MSG, appId)));

		if (appClient.getAppClientDevelopers() == null)
			return false;

		for (DashboardUser user : appClient.getAppClientDevelopers()) {
			if (user.getEmail().equalsIgnoreCase(email)) {
				for (Privilege priv : user.getPrivileges()) {
					if (priv.getName().equalsIgnoreCase(APP_CLIENT_DEVELOPER_PRIV)) return true;
				}
			}
		}
		return false;
	}

	/**
	 * Checks if "user" is the app itself associated with given subscription ID or if the "user"
	 * is a developer for the app-client associated with given subscription ID
	 * @param subscriptionId the subscription ID
	 * @param user the user name from request (could be the app client or a user's email)
	 * @return true if above criteria is met
	 */
	@Override
	public boolean userIsAppClientDeveloperForAppSubscription(UUID subscriptionId, String user) {
		SubscriberDto sub = subscriberService.getSubscriberById(subscriptionId);

		// otherwise see if this user is an email associated with a developer for this app client
		AppClientUser app = appClientRepository
				.findByNameIgnoreCase(sub.getAppClientUser())
				.orElseThrow(() -> new RecordNotFoundException(String.format("No app with name %s found", sub.getAppClientUser())));

		// if the subscription has an appclient for which does not have READ access for the type
		//  of entity its subscribed to then return false... so  its not even seen to be edited
		String entityType = SubscriberServiceImpl.getTargetEntityType(sub.getSubscribedEvent());
		if (!app
				.getPrivileges()
				.stream()
				.map(Privilege::getName)
				.collect(Collectors.toList())
				.contains(entityType + "_READ")) return false;

		// if the user from request is the app itself that owns this id then return true,
		if (user.equalsIgnoreCase(sub.getAppClientUser())) return true;

		return userIsAppClientDeveloperForApp(app.getId(), user);

	}

	/**
	 * Private helper that removes one or all developers from an app client
	 * @param appClient the app client entity to modify
	 * @param email email of the developer to remove
	 * @param deleteAll whether to delete all developers (if true negates the email parameter)
	 * @return the modified app client entity
	 */
	private AppClientUser deleteDevelopersFromAppClient(AppClientUser appClient, String email, boolean deleteAll) {
		if (appClient.getAppClientDevelopers() == null) return appClient;

		for (DashboardUser user : new HashSet<>(appClient.getAppClientDevelopers())) {
			if (!deleteAll && !user.getEmail().equalsIgnoreCase(email)) continue;

			// at the minimum we remove this user from this app client
			Set<DashboardUser> developers = new HashSet<>(appClient.getAppClientDevelopers());
			developers.remove(user);
			appClient.setAppClientDevelopers(developers);
			appClient = appClientRepository.saveAndFlush(appClient);

			List<AppClientUser> userClientApps = appClientRepository.findByAppClientDevelopersContaining(user);
			if (userClientApps.isEmpty() && !privSetHasPrivsOtherThanAppClient(user.getPrivileges())) {

				// this user doesn't belong to other apps and has no other privileges
				//   so delete completely
				dashboardUserService.deleteDashboardUser(user.getId());
			}
			else if (userClientApps.isEmpty() && privSetHasPrivsOtherThanAppClient(user.getPrivileges())) {

				// this user doesn't belong to other app client apps but HAS other privileges
				//   in the system, so just remove the APP_CLIENT_DEVELOPER priv since keeping it
				//   would make it an "orphaned" and unnecessary privilege
				Set<Privilege> userPrivs = new HashSet<>(user.getPrivileges());
				userPrivs.removeIf(p -> p.getName().equalsIgnoreCase(APP_CLIENT_DEVELOPER_PRIV));
				user.setPrivileges(userPrivs);
				dashboardUserRepository.save(user);
			}
		}

		return appClient;
	}

	/**
	 * Private helper to examine a set of privileges to see if there's an other-than-app-client-developer
	 * and dashboard user privs in it... used for removing Dashboard user from an App Client.
	 * @param privs set of privileges to examine
	 * @return if there's other privileges than App Client Developer
	 */
	private boolean privSetHasPrivsOtherThanAppClient(Set<Privilege> privs) {
		for (Privilege priv : privs) {
			if (!priv.getName().equals(APP_CLIENT_DEVELOPER_PRIV)
					&& !priv.getName().equals("DASHBOARD_USER")) return true;
		}
		return false;
	}

	/**
	 * Deletes an app client developer with given DashboardUser from all app clients he/she may be a developer of
	 * @param user DashboardUser to search and delete from app client(s)
	 */
	@Override
	public void deleteDeveloperFromAllAppClient(DashboardUser user) {
		List<AppClientUser> usersAppSources = appClientRepository.findByAppClientDevelopersContaining(user);
		for (AppClientUser appClient : usersAppSources) {
			appClient.getAppClientDevelopers().remove(user);
			appClientRepository.saveAndFlush(appClient);
		}
	}

	@Override
	public Iterable<AppClientUser> getAppClientUsersContainingDeveloperEmail(String developerEmail) {
		return appClientRepository.findByAppClientDevelopersEmailIgnoreCase(developerEmail);
	}
	
	/**
	 * Performs sanitization for a dto.
	 * 
	 * @param clientUserDto the dto to sanitize
	 * @return sanitized dto
	 */
	private AppClientUserDto sanitizeAppClientUserDto(AppClientUserDto clientUserDto) {
		sanitizeEfaPrivileges(clientUserDto.getPrivileges());
		
		return clientUserDto;
	}
	
	/**
	 * Removes unused Entity Field Authorization privileges from a list of privilege dtos.
	 * For example, if PERSON_EDIT is not included in {@code privileges}, then any 
	 * Person- prefixed privilege will be removed. The removals happen in place.
	 * 
	 * @param privileges the list of privileges to sanitize
	 */
	private void sanitizeEfaPrivileges(List<PrivilegeDto> privileges) {
		boolean hasPersonEdit = privileges.stream().anyMatch(privilege -> privilege.getName().equals("PERSON_EDIT"));
		boolean hasOrgEdit = privileges.stream().anyMatch(privilege -> privilege.getName().equals("ORGANIZATION_EDIT"));

		List<PrivilegeDto> privilegesToBeSanitized = privileges.stream().filter(privilege -> {
			return (!hasPersonEdit && privilege.getName().startsWith("Person-")) || 
					(!hasOrgEdit && privilege.getName().startsWith("Organization-"));
		}).collect(Collectors.toList());
		
		privileges.removeAll(privilegesToBeSanitized);
	}
}
