package mil.tron.commonapi.service;

import java.util.ArrayList;

import java.util.List;
import java.util.Set;

import javax.transaction.Transactional;

import mil.tron.commonapi.entity.DashboardUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.AuthenticationUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Service;

import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.DashboardUserRepository;

/**
 * PreAuthenticated service for use with Client Users that is an Application.
 *
 */
@Service
public class AppClientUserPreAuthenticatedService implements AuthenticationUserDetailsService<PreAuthenticatedAuthenticationToken> {
	
	private AppClientUserRespository repository;
	private DashboardUserRepository dashboardUserRepository;
	
	public AppClientUserPreAuthenticatedService(AppClientUserRespository repository) {
		this.repository = repository;
	}

	@Transactional
	@Override
	public UserDetails loadUserDetails(PreAuthenticatedAuthenticationToken token) throws UsernameNotFoundException {
		String userDetailKey = token.getName();

		List<GrantedAuthority> privileges = new ArrayList<>();

		if (userDetailKey.contains("@")) {

//			DashboardUser dashboardUser = dashboardUserRepository.findByEmail
//			privileges = createPrivileges(dashboardUser.getPrivileges());
		} else {
			AppClientUser user = repository.findByNameIgnoreCase(token.getName()).orElseThrow(() -> new UsernameNotFoundException("Username not found: " + token.getName()));
			privileges = createPrivileges(user.getPrivileges());
		}


		return new User(userDetailKey, "N/A", privileges);
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
