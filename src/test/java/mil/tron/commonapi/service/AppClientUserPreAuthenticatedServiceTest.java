package mil.tron.commonapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.repository.AppClientUserRespository;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class AppClientUserPreAuthenticatedServiceTest {
	@Mock
	private AppClientUserRespository repository;
	
	@InjectMocks
	private AppClientUserPreAuthenticatedService service;
	
	private PreAuthenticatedAuthenticationToken token = Mockito.mock(PreAuthenticatedAuthenticationToken.class);
	
	private AppClientUser user;
	
	@BeforeEach
	void setup() {
		Set<Privilege> privileges = new HashSet<>();
		
		Privilege privilege = new Privilege();
		privilege.setId(1L);
		privilege.setName("READ");
		
		privileges.add(privilege);
		
		user = new AppClientUser();
		user.setId(UUID.randomUUID());
		user.setName("Test_User");
		user.setPrivileges(privileges);
	}
	
	@Test
	void testLoadUser() {
		Mockito.when(repository.findByNameIgnoreCase(user.getName())).thenReturn(Optional.of(user));
		Mockito.when(token.getName()).thenReturn(user.getName());
		
		UserDetails resultUser = service.loadUserDetails(token);
		
		assertThat(resultUser.getUsername()).isEqualTo(user.getName());
		assertThat(resultUser.getAuthorities()).hasSize(1);
	}
	
	@Test
	void testLoadUser_noPrivileges() {
		Mockito.when(repository.findByNameIgnoreCase(user.getName())).thenReturn(Optional.of(user));
		Mockito.when(token.getName()).thenReturn(user.getName());
		
		user.setPrivileges(null);
		
		UserDetails resultUser = service.loadUserDetails(token);
		
		assertThat(resultUser.getAuthorities()).isEmpty();
	}
	
	@Test
	void testLoadUser_notExists() {
		Mockito.when(repository.findByNameIgnoreCase(user.getName())).thenReturn(Optional.ofNullable(null));
		Mockito.when(token.getName()).thenReturn(user.getName());
		
		assertThrows(UsernameNotFoundException.class, () -> service.loadUserDetails(token));
	}
}
