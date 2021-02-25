package mil.tron.commonapi.service;

import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
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
import java.util.Set;

/**
 * PreAuthenticated service for use with Client Users that is an Application.
 *
 */
@Service
public class AppClientUserPreAuthenticatedService implements AuthenticationUserDetailsService<PreAuthenticatedAuthenticationToken> {

	private AppClientUserRespository appClientUserRespository;
	private DashboardUserRepository dashboardUserRepository;
	private PrivilegeRepository privilegeRepository;
	private static final String NoCredentials = "NoCredentials";

	@Value("${common-api-app-name}")
	private String commonApiAppName;

	public AppClientUserPreAuthenticatedService(AppClientUserRespository appClientUserRespository,
												PrivilegeRepository privilegeRepository,
												DashboardUserRepository dashboardUserRepository) {
		this.appClientUserRespository = appClientUserRespository;
		this.privilegeRepository = privilegeRepository;
		this.dashboardUserRepository = dashboardUserRepository;
	}

	@Transactional
	@Override
	public UserDetails loadUserDetails(PreAuthenticatedAuthenticationToken token) throws UsernameNotFoundException {
		if (token.getName().equals(this.commonApiAppName)) {
			// pull dashboard user by credential/email
			if (!token.getCredentials().equals(NoCredentials)) {
				DashboardUser dashboardUser = dashboardUserRepository.findByEmailIgnoreCase(token.getCredentials().toString()).orElseThrow(() -> new UsernameNotFoundException("Dashboard User not found: " + token.getCredentials().toString()));
				List<GrantedAuthority> dashboardUserPrivileges = createPrivileges(dashboardUser.getPrivileges());
				return new User(dashboardUser.getEmail(), "N/A", dashboardUserPrivileges);
			}
			else {
				throw new RecordNotFoundException("Error you are not a dashboard user.");
			}
		}
		AppClientUser user = appClientUserRespository.findByNameIgnoreCase(token.getName()).orElseThrow(() -> new UsernameNotFoundException("App Client name not found: " + token.getName()));
		List<GrantedAuthority> privileges = createPrivileges(user.getPrivileges());

		return new User(user.getName(), "N/A", privileges);
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
}
