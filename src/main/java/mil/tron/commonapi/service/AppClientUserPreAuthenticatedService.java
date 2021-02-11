package mil.tron.commonapi.service;

import java.util.ArrayList;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.transaction.Transactional;

import lombok.val;
import mil.tron.commonapi.exception.RecordNotFoundException;
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

import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.repository.AppClientUserRespository;

/**
 * PreAuthenticated service for use with Client Users that is an Application.
 *
 */
@Service
public class AppClientUserPreAuthenticatedService implements AuthenticationUserDetailsService<PreAuthenticatedAuthenticationToken> {
	
	private AppClientUserRespository appClientUserRespository;

	private PrivilegeRepository privilegeRepository;

	@Value("${common-api-app-name}")
	private String commonApiAppName;
	
	public AppClientUserPreAuthenticatedService(AppClientUserRespository appClientUserRespository,
												PrivilegeRepository privilegeRepository) {
		this.appClientUserRespository = appClientUserRespository;
		this.privilegeRepository = privilegeRepository;
	}

	@Transactional
	@Override
	public UserDetails loadUserDetails(PreAuthenticatedAuthenticationToken token) throws UsernameNotFoundException {
		// allow for configured self app hostname to have all privileges
		if (token.getName().equals(this.commonApiAppName)) {
			val privileges = this.privilegeRepository.findAll();
			if (privileges == null) {
				throw new RecordNotFoundException("There are not privileges available.");
			}
			// add all privileges for self app
			Set<Privilege> privilegeSet = StreamSupport.stream(privileges.spliterator(), false)
					.collect(Collectors.toSet());
			return new User(this.commonApiAppName, "N/A", this.createPrivileges(privilegeSet));
		}
		AppClientUser user = appClientUserRespository.findByNameIgnoreCase(token.getName()).orElseThrow(() -> new UsernameNotFoundException("Username not found: " + token.getName()));
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
