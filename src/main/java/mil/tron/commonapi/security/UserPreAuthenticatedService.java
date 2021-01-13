package mil.tron.commonapi.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.transaction.Transactional;

import org.springframework.core.env.Environment;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.AuthenticationUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Service;

import mil.tron.commonapi.entity.Role;
import mil.tron.commonapi.entity.User;
import mil.tron.commonapi.repository.UserRepository;

@Service
public class UserPreAuthenticatedService implements AuthenticationUserDetailsService<PreAuthenticatedAuthenticationToken> {
	
	private UserRepository repository;
	private Environment environment;
	
	public UserPreAuthenticatedService(UserRepository repository, Environment environment) {
		this.repository = repository;
		this.environment = environment;
	}

//	@Transactional
//	@Override
//	public UserDetails loadUserDetails(PreAuthenticatedAuthenticationToken token) throws UsernameNotFoundException {
//		User user = repository.findByNameIgnoreCase(token.getName()).orElseThrow(() -> new UsernameNotFoundException("Username not found: " + token.getName()));
//		List<GrantedAuthority> roles = createRoles(user.getRoles());
//		
//		return new org.springframework.security.core.userdetails.User(user.getName(), "N/A", roles);
//	}
	
//	private List<GrantedAuthority> createRoles(Set<Role> roles) {
//		if (roles == null)
//			return new ArrayList<>();
//		
//		List<GrantedAuthority> authorities = new ArrayList<>();
//		
//		for (Role role : roles) {
//			authorities.add(new SimpleGrantedAuthority(role.getName()));
//		}
//		
//		return authorities;
//	}
	
	@Override
	public UserDetails loadUserDetails(PreAuthenticatedAuthenticationToken token) {
		Boolean userExists = environment.getProperty("authentication." + token.getName(), Boolean.class);
		
		if (userExists == null || !userExists.booleanValue())
			throw new UsernameNotFoundException("Username: " + token.getName() + " does not exist.");
		
		List<String> stringRoles = (List<String>)environment.getProperty("authentication." + token.getName() + ".permissions", List.class);
		List<GrantedAuthority> roles = new ArrayList<>();
		
		if (stringRoles != null) {
			stringRoles.forEach(role -> {
				roles.add(new SimpleGrantedAuthority(role));
			});
		}
		
		return new org.springframework.security.core.userdetails.User(token.getName(), "N/A", roles);
	}
	
}
