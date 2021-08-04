package mil.tron.commonapi.service;

import mil.tron.commonapi.dto.ScratchStorageAppRegistryDto;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.appsource.AppEndpointPriv;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.scratch.ScratchStorageAppRegistryEntryRepository;
import mil.tron.commonapi.service.scratch.ScratchStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.AuthenticationUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PreAuthenticated service for use with Client Users that is an Application.
 *
 */
@Service
public class AppClientUserPreAuthenticatedService implements AuthenticationUserDetailsService<PreAuthenticatedAuthenticationToken> {

	private AppClientUserRespository appClientUserRespository;
	private DashboardUserRepository dashboardUserRepository;
	private static final String NO_CREDS = "NoCredentials";

	/**
	 * The app name of digitize itself (the hosting container's app client name)
	 * The running instance of Tron Common must make digitize an app client like
	 * any other app - and it needs to be named as the DIGITIZE_ROOT_VALUE shown below
	 */
	private static final String DIGITIZE_ROOT_NAME = "digitize";

	/**
	 * Digitize apps that want access to Tron Common Resources must inject a header
	 * with the name below - with its value set to the UUID of the scratch space
	 */
	private static final String DIGITIZE_APP_ID_HEADER = "digitize-id";

	/**
	 * Prefix for digitize app client records (their name needs this prefix)
	 * so we can easily ID them in the audit logs
	 */
	private static final String DIGITIZE_APP_PREFIX = "digitize-";

	@Value("${common-api-app-name}")
	private String commonApiAppName;

	private ScratchStorageService scratchStorageService;

	public AppClientUserPreAuthenticatedService(AppClientUserRespository appClientUserRespository,
												DashboardUserRepository dashboardUserRepository,
												ScratchStorageService scratchStorageService) {
		this.appClientUserRespository = appClientUserRespository;
		this.dashboardUserRepository = dashboardUserRepository;
		this.scratchStorageService = scratchStorageService;
	}

	@Transactional
	@Override
	public UserDetails loadUserDetails(PreAuthenticatedAuthenticationToken token) throws UsernameNotFoundException {
		// pull dashboard user by credential/email if request was from the SSO gateway (e.g from the Internet itself)
		//  then the token's "getName" will return "istio-system", and if it was from the SSO, then the tokens
		//  "getCredentials" will give us the P1 user's email to key off of
		if (token.getName().equals(this.commonApiAppName) && !token.getCredentials().equals(NO_CREDS)) {
			Optional<DashboardUser> dashboardUser = dashboardUserRepository.findByEmailIgnoreCase(token.getCredentials().toString());
			if (dashboardUser.isPresent()) {
				List<GrantedAuthority> dashboardUserPrivileges = createPrivileges(dashboardUser.get().getPrivileges());
				return new User(dashboardUser.get().getEmail(), NO_CREDS, dashboardUserPrivileges);
			}
			else {
				// continue on as a non-dashboard user/admin, if destined for ScratchStorage
				//  their email will be evaluated there for app access
				return new User(token.getCredentials().toString(), NO_CREDS, new ArrayList<>());
			}

		}

		// wasn't from the internet, so probably an App Client
		//  In this case, the token's "getName" will return the istio namespace which
		//  we key off of for the lookup of the name of the app client
		AppClientUser user = appClientUserRespository
				.findByNameIgnoreCase(token.getName())
				.orElseThrow(() -> new UsernameNotFoundException("App Client name not found: " + token.getName()));

		// if the app client is "digitize" then we have a little more to check here
		//   for AuthZ.
		user = checkIfDigitizeAppRequest(token, user);

		List<GrantedAuthority> privileges = createPrivileges(user.getPrivileges());
		privileges.addAll(createGatewayAuthorities(user.getAppEndpointPrivs()));
		privileges.add(new SimpleGrantedAuthority("APP_CLIENT"));  // every app client gets this
		return new User(user.getName(), NO_CREDS, privileges);
	}

	private AppClientUser checkIfDigitizeAppRequest(PreAuthenticatedAuthenticationToken token, AppClientUser user) {

		if (user.getName().equalsIgnoreCase(DIGITIZE_ROOT_NAME)) {
			RequestAttributes reqContext = RequestContextHolder.getRequestAttributes();
			if (reqContext == null) return user;

			HttpServletRequest request = ((ServletRequestAttributes) reqContext).getRequest();
			// check for the DIGITIZE_APP_ID_HEADER presence
			if (request.getHeader(DIGITIZE_APP_ID_HEADER) != null) {
				UUID id;
				try {
					id = UUID.fromString(request.getHeader(DIGITIZE_APP_ID_HEADER));
					ScratchStorageAppRegistryDto dto = scratchStorageService.getRegisteredScratchApp(id);

					// if the user JWT info (email address) is null, then no use continuing
					//  return the regular digitize app client
					if (token.getCredentials() == null) return user;

					// if this scratch space has IMPLICIT_READ enabled -or- the requester's
					//   p1 email address (from JWT) is authorized for this scratch space in any
					//   form then proceed to the app client entity lookup
					//   DIGITIZE_APP_PREFIX + scratch-space-name
					if (dto.isAppHasImplicitRead() || dto
							.getUserPrivs()
							.stream()
							.map(ScratchStorageAppRegistryDto.UserWithPrivs::getEmailAddress)
							.collect(Collectors.toList())
							.contains(token.getCredentials().toString())) {

						return appClientUserRespository
								.findByNameIgnoreCase(DIGITIZE_APP_PREFIX + dto.getAppName())
								.orElseThrow(() -> new UsernameNotFoundException("Digitize Client name not found as App Client: " + dto.getAppName()));
					}
				}
				catch (RecordNotFoundException | IllegalArgumentException e) {
					// return original app client entity on any failure
					//   (UUID was malformed, or scratch space didnt exist)
					return user;
				}
			}
		}

		// if we get here, return the current app client object we received
		return user;
	}

	private List<GrantedAuthority> createPrivileges(Set<Privilege> privileges) {
		if (privileges == null)
			return new ArrayList<>();

		List<GrantedAuthority> authorities = new ArrayList<>();

		for (Privilege privilege : privileges) {
			authorities.add(new SimpleGrantedAuthority(privilege.getName()));
		}

		return authorities;
	}

	private List<GrantedAuthority> createGatewayAuthorities(Set<AppEndpointPriv> privs) {
		if (privs == null)
			return new ArrayList<>();

		List<GrantedAuthority> authorities = new ArrayList<>();

		for(AppEndpointPriv appPriv : privs) {
			if(appPriv.getAppEndpoint() != null && appPriv.getAppEndpoint().getAppSource().getAppSourcePath() != null && appPriv.getAppEndpoint().getAppSource().getAppSourcePath().length() > 0)
				// GrantedAuthority = name of the AppSource + the particular endpoint privilege allowed
				// Ex: example-gateway/example-operation-path
				authorities.add(new SimpleGrantedAuthority(appPriv.getAppEndpoint().getAppSource().getAppSourcePath() + appPriv.getAppEndpoint().getPath() + "_" + appPriv.getAppEndpoint().getMethod().toString()));
		}

		return authorities;
	}
}
