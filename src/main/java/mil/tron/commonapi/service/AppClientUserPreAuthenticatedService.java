package mil.tron.commonapi.service;

import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.appsource.AppEndpointPriv;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.DashboardUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.AuthenticationUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * PreAuthenticated service for use with Client Users that is an Application.
 *
 */
@Service
public class AppClientUserPreAuthenticatedService implements AuthenticationUserDetailsService<PreAuthenticatedAuthenticationToken> {

	private AppClientUserRespository appClientUserRespository;
	private DashboardUserRepository dashboardUserRepository;
	private static final String NO_CREDS = "NoCredentials";

	@Value("${common-api-app-name}")
	private String commonApiAppName;

	public AppClientUserPreAuthenticatedService(AppClientUserRespository appClientUserRespository,
												DashboardUserRepository dashboardUserRepository) {
		this.appClientUserRespository = appClientUserRespository;
		this.dashboardUserRepository = dashboardUserRepository;
	}

	@Transactional
	@Override
	public UserDetails loadUserDetails(PreAuthenticatedAuthenticationToken token) throws UsernameNotFoundException {

		// pull dashboard user by credential/email if request was from the SSO gateway
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
		AppClientUser user = appClientUserRespository.findByNameIgnoreCase(token.getName()).orElseThrow(() -> new UsernameNotFoundException("App Client name not found: " + token.getName()));
		List<GrantedAuthority> privileges = createPrivileges(user.getPrivileges());
		privileges.addAll(createGatewayAuthorities(user.getAppEndpointPrivs()));
		privileges.add(new SimpleGrantedAuthority("APP_CLIENT"));  // every app client gets this
		return new User(user.getName(), NO_CREDS, privileges);
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
