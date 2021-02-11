package mil.tron.commonapi.service;

import java.util.ArrayList;

import java.util.List;
import java.util.Set;

import javax.transaction.Transactional;

import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.repository.PersonRepository;
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
	
	private AppClientUserRespository repository;
	private PersonRepository personRepository;
	
	public AppClientUserPreAuthenticatedService(AppClientUserRespository repository, PersonRepository personRepository) {
		this.repository = repository;
		this.personRepository = personRepository;
	}

	@Value("${istio-gateway-name}")
	private String istioGatewayName;

	@Transactional
	@Override
	public UserDetails loadUserDetails(PreAuthenticatedAuthenticationToken token) throws UsernameNotFoundException {

		// first vet the application bringing us the request, could be istio ingress, or another app...
		// throw right away if the app name from the x-forwarded-client-cert (XFCC) isn't even registered with common api
		AppClientUser user = repository.findByNameIgnoreCase(token.getName()).orElseThrow(() -> new UsernameNotFoundException("Application name not found: " + token.getName()));

		if (user.getName().equals(istioGatewayName)) {
			// this is a request from a user forwarded to us by the Istio Gateway (from a user's browser, thru the SSO)
			// first, see if the user is even in the Common API system -> if not, then throw
			// second, check what the user can do
			Person person = personRepository.findByEmailIgnoreCase(token.getCredentials().toString()).orElseThrow(() ->
					new UsernameNotFoundException("Istio user with email: " + token.getName() + " not found"));

			return new User(person.getEmail(), "N/A", createPrivileges(person.getPrivileges()));
		}
		else {
			// this request is directly from another app in the cluster, and is registered with Common API
			//  get the privs registered for the application and proceed on...
			List<GrantedAuthority> privileges = createPrivileges(user.getPrivileges());
			return new User(user.getName(), "N/A", privileges);
		}

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
