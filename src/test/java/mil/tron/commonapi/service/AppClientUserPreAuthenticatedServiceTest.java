package mil.tron.commonapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.bind.annotation.RequestMethod;

import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.entity.appsource.AppEndpoint;
import mil.tron.commonapi.entity.appsource.AppEndpointPriv;
import mil.tron.commonapi.repository.AppClientUserRespository;

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
	void testLoadUserWithGatewayPrivileges() {
		AppSource appSource = AppSource.builder()
			.appSourcePath("test-gateway")
			.id(UUID.randomUUID())
			.name("Test Gateway")
			.openApiSpecFilename("test.yml")
			.build();
		AppEndpoint appEndpoint = AppEndpoint.builder()
			.appSource(appSource)
			.id(UUID.randomUUID())
			.method(RequestMethod.GET)
			.path("/path")
			.build();
		AppEndpointPriv appSourcePriv = AppEndpointPriv.builder()
			.appSource(appSource)
			.id(UUID.randomUUID())
			.appEndpoint(appEndpoint)
			.build();
		Set<AppEndpointPriv> appSourcePrivs = new HashSet<AppEndpointPriv>(Arrays.asList(appSourcePriv));
		appSource.setAppPrivs(appSourcePrivs);
		user.setAppEndpointPrivs(appSourcePrivs);
		Mockito.when(repository.findByNameIgnoreCase(user.getName())).thenReturn(Optional.of(user));
		Mockito.when(token.getName()).thenReturn(user.getName());
		
		UserDetails resultUser = service.loadUserDetails(token);
		
		assertThat(resultUser.getUsername()).isEqualTo(user.getName());
		assertThat(resultUser.getAuthorities()).hasSize(2);
		assertThat(resultUser.getAuthorities().stream().anyMatch(auth -> auth.getAuthority().equals("test-gatewayREAD")));
	}

	@Test
	void testLoadUserWithMalformedGatewayPrivileges() {
		AppSource appSource = AppSource.builder()
			.id(UUID.randomUUID())
			.name("Test Gateway")
			.openApiSpecFilename("test.yml")
			.build();
		AppEndpoint appEndpoint = AppEndpoint.builder()
			.appSource(appSource)
			.id(UUID.randomUUID())
			.method(RequestMethod.GET)
			.path("/path")
			.build();
		AppEndpointPriv appSourcePriv = AppEndpointPriv.builder()
			.appSource(appSource)
			.id(UUID.randomUUID())
			.appEndpoint(appEndpoint)
			.build();

		AppSource appSource2 = AppSource.builder()
			.id(UUID.randomUUID())
			.appSourcePath("")
			.name("Other Test Gateway")
			.openApiSpecFilename("test.yml")
			.build();
		AppEndpoint appEndpoint2 = AppEndpoint.builder()
			.appSource(appSource2)
			.id(UUID.randomUUID())
			.method(RequestMethod.GET)
			.path("/path2")
			.build();
		AppEndpointPriv appSourcePriv2 = AppEndpointPriv.builder()
			.appSource(appSource2)
			.id(UUID.randomUUID())
			.appEndpoint(appEndpoint2)
			.build();
		Set<AppEndpointPriv> appSourcePrivs = new HashSet<AppEndpointPriv>(Arrays.asList(appSourcePriv, appSourcePriv2));
		appSource.setAppPrivs(appSourcePrivs);
		user.setAppEndpointPrivs(appSourcePrivs);
		Mockito.when(repository.findByNameIgnoreCase(user.getName())).thenReturn(Optional.of(user));
		Mockito.when(token.getName()).thenReturn(user.getName());
		
		UserDetails resultUser = service.loadUserDetails(token);
		
		assertThat(resultUser.getUsername()).isEqualTo(user.getName());
		assertThat(resultUser.getAuthorities()).hasSize(1);
		assertThat(resultUser.getAuthorities().stream().allMatch(auth -> auth.getAuthority().equals("READ")));
	}

	@Test
	void testLoadUserWithNoAppSourcePrivs() {
		AppSource appSource = AppSource.builder()
			.id(UUID.randomUUID())
			.name("Test Gateway")
			.openApiSpecFilename("test.yml")
			.build();
		Set<AppEndpointPriv> appSourcePrivs = null;
		appSource.setAppPrivs(appSourcePrivs);
		user.setAppEndpointPrivs(appSourcePrivs);
		Mockito.when(repository.findByNameIgnoreCase(user.getName())).thenReturn(Optional.of(user));
		Mockito.when(token.getName()).thenReturn(user.getName());
		
		UserDetails resultUser = service.loadUserDetails(token);
		
		assertThat(resultUser.getUsername()).isEqualTo(user.getName());
		assertThat(resultUser.getAuthorities()).hasSize(1);
		assertThat(resultUser.getAuthorities().stream().allMatch(auth -> auth.getAuthority().equals("READ")));
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
