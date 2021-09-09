package mil.tron.commonapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import com.google.common.collect.Lists;
import mil.tron.commonapi.dto.PrivilegeDto;
import mil.tron.commonapi.dto.ScratchStorageAppRegistryDto;
import mil.tron.commonapi.entity.scratch.ScratchStorageUser;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.scratch.ScratchStorageAppRegistryEntryRepository;
import mil.tron.commonapi.service.scratch.ScratchStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class AppClientUserPreAuthenticatedServiceTest {
	@Mock
	private AppClientUserRespository repository;

	@Mock
	private ScratchStorageService scratchService;
	
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
		assertTrue(resultUser.getAuthorities().contains(new SimpleGrantedAuthority("READ")));
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
		assertTrue(resultUser.getAuthorities().contains(new SimpleGrantedAuthority("READ")));
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
		assertTrue(resultUser.getAuthorities().contains(new SimpleGrantedAuthority("READ")));
	}
	
	@Test
	void testLoadUser_noPrivileges() {
		Mockito.when(repository.findByNameIgnoreCase(user.getName())).thenReturn(Optional.of(user));
		Mockito.when(token.getName()).thenReturn(user.getName());
		
		user.setPrivileges(null);
		
		UserDetails resultUser = service.loadUserDetails(token);

		assertFalse(resultUser.getAuthorities().contains(new SimpleGrantedAuthority("READ")));
	}
	
	@Test
	void testLoadUser_notExists() {
		Mockito.when(repository.findByNameIgnoreCase(user.getName())).thenReturn(Optional.ofNullable(null));
		Mockito.when(token.getName()).thenReturn(user.getName());
		
		assertThrows(UsernameNotFoundException.class, () -> service.loadUserDetails(token));
	}

	@Test
	void testDigitizeAuthorizedApp() {
		UUID appId = UUID.randomUUID();
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("digitize-id", appId);
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

		ScratchStorageUser scratchStorageUser = ScratchStorageUser.builder()
				.id(UUID.randomUUID())
				.email("scratch1@test.com")
				.build();

		AppClientUser digitizeClient = AppClientUser
				.builder()
				.name("digitize")
				.build();

		AppClientUser digitizeTestApp = AppClientUser
				.builder()
				.name("digitize-TestDigitizeApp")
				.build();

		Mockito.when(repository.findByNameIgnoreCase(digitizeClient.getName()))
				.thenReturn(Optional.of(digitizeClient));

		Mockito.when(repository.findByNameIgnoreCase(digitizeTestApp.getName()))
				.thenReturn(Optional.of(digitizeTestApp));

		Mockito.when(token.getName())
				.thenReturn("digitize");

		Mockito.when(token.getCredentials())
				.thenReturn(null)
				.thenReturn(scratchStorageUser.getEmail());

		List<ScratchStorageAppRegistryDto.UserWithPrivs> privs = Lists.newArrayList(
			ScratchStorageAppRegistryDto.UserWithPrivs.builder()
				.userId(UUID.randomUUID())
				.emailAddress(scratchStorageUser.getEmail())
				.privs(Lists.newArrayList(ScratchStorageAppRegistryDto.PrivilegeIdPair.builder()
						.userPrivPairId(UUID.randomUUID())
						.priv(PrivilegeDto.builder().id(1L).build())
						.build()))
				.build());

		ScratchStorageAppRegistryDto theApp = ScratchStorageAppRegistryDto
				.builder()
				.appHasImplicitRead(true)
				.appName("TestDigitizeApp")
				.userPrivs(privs)
				.build();

		Mockito.when(scratchService.getRegisteredScratchApp(appId)).thenReturn(theApp);


		// test P1 JWT creds are somehow missing
		//   result in regular digitize permissions prevailing
		assertEquals("digitize", service.loadUserDetails(token).getUsername());

		// go path
		assertEquals(digitizeTestApp.getName(), service.loadUserDetails(token).getUsername());

		// everything exists, but scratch app does not have Implicit Read, but requester does have privs with it
		theApp.setAppHasImplicitRead(false);
		assertEquals(digitizeTestApp.getName(), service.loadUserDetails(token).getUsername());

		// everything exists but scratch app does not have Implicit Read and the requester does NOT have privs with it
		//  access denied
		theApp.setAppHasImplicitRead(true);
		theApp.setUserPrivs(Lists.newArrayList());
		assertEquals("digitize", service.loadUserDetails(token).getUsername());

		// everything exists but scratch app does not have Implicit Read and the requester does NOT have privs with it
		//   request should then follow privs of regular digitize app privs
		theApp.setAppHasImplicitRead(false);
		theApp.setUserPrivs(Lists.newArrayList());
		assertEquals("digitize", service.loadUserDetails(token).getUsername());

		// test scratch app not found
		Mockito.when(scratchService.getRegisteredScratchApp(appId)).thenThrow(new RecordNotFoundException("App Not Found"));
		assertEquals("digitize", service.loadUserDetails(token).getUsername());

		// test no digitize app even found
		Mockito.when(repository.findByNameIgnoreCase(digitizeClient.getName()))
				.thenReturn(Optional.empty());
		assertThrows(UsernameNotFoundException.class, () -> service.loadUserDetails(token).getUsername());
	}
}
