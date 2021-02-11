package mil.tron.commonapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.repository.PersonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.repository.AppClientUserRespository;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AppClientUserPreAuthenticatedServiceTest {

	@Mock
	private PersonRepository personRepository;

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

	@Test
	void testRequestIsFromIstio() {

		// test that a request is from the SSO gateway and get user email from creds and do lookup in Person table

		String istioGatewayName = "istio-system";
		ReflectionTestUtils.setField(service, "istioGatewayName", istioGatewayName);

		user = new AppClientUser();
		user.setId(UUID.randomUUID());
		user.setName(istioGatewayName);

		Person p = Person.builder()
				.id(UUID.randomUUID())
				.email("someone@test.com")
				.build();

		Mockito.when(token.getName()).thenReturn(istioGatewayName);
		Mockito.when(token.getCredentials()).thenReturn(p.getEmail());
		Mockito.when(repository.findByNameIgnoreCase(istioGatewayName)).thenReturn(Optional.of(user));
		Mockito.when(personRepository.findByEmailIgnoreCase(p.getEmail())).thenReturn(Optional.of(p));

		UserDetails resultUser = service.loadUserDetails(token);
		assertThat(resultUser.getUsername()).isEqualTo(p.getEmail());

		Mockito.when(personRepository.findByEmailIgnoreCase(p.getEmail())).thenReturn(Optional.empty());
		assertThrows(UsernameNotFoundException.class, () -> service.loadUserDetails(token));
	}
}
