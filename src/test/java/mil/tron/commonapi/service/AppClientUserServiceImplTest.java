package mil.tron.commonapi.service;

import com.google.common.collect.Sets;
import mil.tron.commonapi.dto.appclient.AppClientSummaryDto;
import mil.tron.commonapi.dto.appclient.AppClientUserDto;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import mil.tron.commonapi.repository.appsource.AppSourceRepository;
import mil.tron.commonapi.service.pubsub.SubscriberService;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doNothing;

@ExtendWith(MockitoExtension.class)
class AppClientUserServiceImplTest {
	private static final DtoMapper MODEL_MAPPER = new DtoMapper();
	private static final String APP_CLIENT_DEVELOPER_PRIV = "APP_CLIENT_DEVELOPER";

	@Mock
	private AppClientUserRespository repository;

	@Mock
	private AppSourceRepository appSourceRepository;

	@Mock
	private PrivilegeRepository privilegeRepository;

	@Mock
	private DashboardUserServiceImpl dashboardUserService;

	@Mock
	private DashboardUserRepository dashboardUserRepository;

	@Mock
	private SubscriberService subscriberService;
	
	@InjectMocks
	private AppClientUserServiceImpl userService;
	
	private List<AppClientUser> users;
	private AppClientUser user;
	private AppClientUserDto userDto;

	private Privilege appClientDev, dashBoardUser;
	private DashboardUser appClientDevPerson;
	
	@BeforeEach
	void setup() {

		appClientDev = Privilege.builder()
				.id(1L)
				.name(APP_CLIENT_DEVELOPER_PRIV)
				.build();

		dashBoardUser = Privilege.builder()
				.id(2L)
				.name("DASHBOARD_USER")
				.build();

		appClientDevPerson = DashboardUser.builder()
				.email("joe@test.com")
				.privileges(Set.of(dashBoardUser, appClientDev))
				.build();

		users = new ArrayList<>();
		
		user = new AppClientUser();
		user.setId(UUID.randomUUID());
		user.setName("User A");
		user.setPrivileges(new HashSet<Privilege>());
		
		userDto = MODEL_MAPPER.map(user, AppClientUserDto.class);
		
		users.add(user);
	}
	
	@Test
    void getAppClientUsersTest() {
    	Mockito.when(repository.findByAvailableAsAppClientTrue()).thenReturn(users);
    	Iterable<AppClientUserDto> appUsers = userService.getAppClientUsers();
    	List<AppClientUserDto> result = StreamSupport.stream(appUsers.spliterator(), false).collect(Collectors.toList());
    	assertThat(result).hasSize(1);
    	assertThat(result.get(0)).isEqualTo(MODEL_MAPPER.map(users.get(0), AppClientUserDto.class));
    }

	@Test
	void getAppClientUsersSummariesTest() {
		Mockito.when(repository.findByAvailableAsAppClientTrue()).thenReturn(users);
		List<AppClientSummaryDto> result = Lists.newArrayList(userService.getAppClientUserSummaries());
		assertThat(result.get(0).getId()).isEqualTo(users.get(0).getId());
	}

	@Test
	void getAppClientUsersSingle() {
		Mockito.when(repository.findById(Mockito.any()))
				.thenReturn(Optional.of(users.get(0)))
				.thenThrow(new RecordNotFoundException("Record not found"));

		assertThat(userService.getAppClient(users.get(0).getId()).getId()).isEqualTo(users.get(0).getId());
		assertThrows(RecordNotFoundException.class, () -> userService.getAppClient(users.get(0).getId()));
	}
	
	@Nested 
	class CreateAppClientUserTest {
		@Test
		void success() {
			Mockito.when(repository.findByNameIgnoreCase(user.getName())).thenReturn(Optional.ofNullable(null));
			Mockito.when(repository.saveAndFlush(Mockito.any(AppClientUser.class))).thenReturn(user);
			
			AppClientUserDto result = userService.createAppClientUser(userDto);
			assertThat(result).isEqualTo(userDto);
		}

		@Test
		void successWhenAppSourceExists() {
			AppClientUser clientUser = AppClientUser.builder()
				.id(user.getId())
				.name(user.getName())
				.availableAsAppClient(false)
				.build();
			Mockito.when(repository.findByNameIgnoreCase(user.getName())).thenReturn(Optional.of(clientUser));
			Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(clientUser));
			Mockito.when(repository.saveAndFlush(Mockito.any(AppClientUser.class))).thenReturn(user);
			
			AppClientUserDto result = userService.createAppClientUser(userDto);
			assertThat(result).isEqualTo(userDto);
		}
		
		@Test
		void resourceAlreadyExists() {
			Mockito.when(repository.findByNameIgnoreCase(user.getName())).thenReturn(Optional.of(user));
			
			assertThrows(ResourceAlreadyExistsException.class, () -> userService.createAppClientUser(userDto));
		}
	}
	
	@Nested
	class UpdateAppClientUser {
		@Test
		void idNotMatching() {
	    	assertThrows(InvalidRecordUpdateRequest.class, () -> userService.updateAppClientUser(UUID.randomUUID(), userDto));
		}
		
		@Test
		void idNotExist() {
			// Test id not exist
	    	Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.ofNullable(null));
	    	assertThrows(RecordNotFoundException.class, () -> userService.updateAppClientUser(userDto.getId(), userDto));
		}
		
		@Test
		void nameAlreadyExists() {
			String changedName = "Some different name";
			userDto.setName(changedName);
			
			AppClientUser existingUser = new AppClientUser();
			existingUser.setId(UUID.randomUUID());
			existingUser.setName(changedName);
			existingUser.setPrivileges(new HashSet<>());
			
			Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(user));
			Mockito.when(repository.findByNameIgnoreCase(Mockito.anyString())).thenReturn(Optional.of(existingUser));
			
			assertThrows(InvalidRecordUpdateRequest.class, () -> userService.updateAppClientUser(userDto.getId(), userDto));
		}
		
		@Test
		void successfulUpdate_NameChange() {
			userDto.setName("Some Different Name");
			
	    	Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(user));
	    	Mockito.when(repository.findByNameIgnoreCase(Mockito.anyString())).thenReturn(Optional.ofNullable(null));
	    	Mockito.when(repository.saveAndFlush(Mockito.any(AppClientUser.class))).thenReturn(MODEL_MAPPER.map(userDto, AppClientUser.class));
	    	
	    	AppClientUserDto updatedUser = userService.updateAppClientUser(userDto.getId(), userDto);
	    	assertThat(updatedUser).isEqualTo(userDto);
		}
		
		@Test
		void successfulUpdate_NoNameChange() {
	    	Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(user));
	    	Mockito.when(repository.saveAndFlush(Mockito.any(AppClientUser.class))).thenReturn(user);

	    	
	    	userDto.setName(null);
	    	AppClientUserDto updatedUser = userService.updateAppClientUser(userDto.getId(), userDto);
	    	assertThat(updatedUser).isEqualTo(MODEL_MAPPER.map(user, AppClientUserDto.class));
		}
	}
	
	@Nested
	class DeleteAppClientTest {
		@Test
	    void deleteAppClient_notExists() {
			assertThrows(RecordNotFoundException.class, () -> userService.deleteAppClientUser(userDto.getId())); 
	    }
		
		@Test
		void deleteAppClient() {
			Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(user));
			Mockito.when(appSourceRepository.existsByIdAndAvailableAsAppSourceTrue(Mockito.any(UUID.class))).thenReturn(false);
			doNothing().when(subscriberService).cancelSubscriptionsByAppClient(Mockito.any(AppClientUser.class));
			AppClientUserDto deletedDto = userService.deleteAppClientUser(userDto.getId());
			assertThat(deletedDto).isEqualTo(userDto);
		}

		@Test
		void deleteAppClientWhenAppSourceWithSameNameExists() {			
			Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(user));			
			Mockito.when(appSourceRepository.existsByIdAndAvailableAsAppSourceTrue(Mockito.any(UUID.class))).thenReturn(true);
			doNothing().when(subscriberService).cancelSubscriptionsByAppClient(Mockito.any(AppClientUser.class));
			AppClientUserDto deletedDto = userService.deleteAppClientUser(userDto.getId());
			
			assertThat(deletedDto).isEqualTo(userDto);
			assertThat(user.getAppClientDevelopers()).isNullOrEmpty();
			assertThat(user.getAppEndpointPrivs()).isNullOrEmpty();
			assertThat(user.getClusterUrl()).isNull();
			assertThat(user.isAvailableAsAppClient()).isFalse();
		}
	}

	@Test
	void testAddAppClientDeveloper() {

		AppClientUser newAppClient = AppClientUser.builder()
				.id(UUID.randomUUID())
				.name("Some App")
				.appClientDevelopers(new HashSet<>())
				.build();

		AppClientUserDto newAppClientDto = AppClientUserDto.builder()
				.id(newAppClient.getId())
				.name(newAppClient.getName())
				.appClientDeveloperEmails(new ArrayList<>())
				.build();

		DashboardUser newUser = DashboardUser.builder()
				.id(UUID.randomUUID())
				.email("dude@dude.com")
				.privileges(Set.of(appClientDev))
				.build();

		DashboardUser newUser2 = DashboardUser.builder()
				.id(UUID.randomUUID())
				.email("dude2@dude.com")
				.privileges(new HashSet<>())
				.build();

		Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(newAppClient));
		Mockito.when(repository.saveAndFlush(Mockito.any())).thenReturn(newAppClient);
		Mockito.when(privilegeRepository.findByName(Mockito.any()))
				.thenReturn(Optional.of(appClientDev));
		Mockito.when(dashboardUserRepository.findByEmailIgnoreCase("dude@dude.com"))
				.thenReturn(Optional.of(newUser));

		Mockito.when(dashboardUserRepository.findByEmailIgnoreCase("dude2@dude.com"))
				.thenReturn(Optional.of(newUser2))
				.thenReturn(Optional.empty());

		// add existing user
		newAppClientDto.setAppClientDeveloperEmails(Lists.newArrayList(newUser.getEmail()));
		userService.updateAppClientUser(newAppClientDto.getId(), newAppClientDto);
		assertThat(newAppClientDto.getAppClientDeveloperEmails().size()).isEqualTo(1);

		// add brand new user
		newAppClientDto.setAppClientDeveloperEmails(Lists.newArrayList(newUser.getEmail(), newUser2.getEmail()));
		userService.updateAppClientUser(newAppClientDto.getId(), newAppClientDto);
		assertThat(newAppClientDto.getAppClientDeveloperEmails().size()).isEqualTo(2);

		// take away the brand new user from the app client
		newAppClientDto.setAppClientDeveloperEmails(Lists.newArrayList(newUser.getEmail()));
		userService.updateAppClientDeveloperItems(newAppClientDto.getId(), newAppClientDto);
		assertThat(newAppClientDto.getAppClientDeveloperEmails().size()).isEqualTo(1);

		// do it again but with bad args
		newAppClientDto.setAppClientDeveloperEmails(Lists.newArrayList(newUser.getEmail()));
		assertThrows(InvalidRecordUpdateRequest.class, () -> userService.updateAppClientDeveloperItems(UUID.randomUUID(), newAppClientDto));

		// do it again but pretend we can't find the record
		Mockito.when(repository.findById(Mockito.any())).thenReturn(Optional.empty());
		assertThrows(RecordNotFoundException.class, () -> userService.updateAppClientDeveloperItems(newAppClientDto.getId(), newAppClientDto));
	}

	@Test
	void testUserIsDeveloperForAppClient() {
		DashboardUser newUser = DashboardUser.builder()
				.id(UUID.randomUUID())
				.email("dude@dude.com")
				.privileges(Set.of(appClientDev))
				.build();

		AppClientUser newAppClient = AppClientUser.builder()
				.id(UUID.randomUUID())
				.name("Some App")
				.appClientDevelopers(Set.of(newUser))
				.build();

		Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(newAppClient));

		assertTrue(userService.userIsAppClientDeveloperForApp(newAppClient.getId(), newUser.getEmail()));
		assertFalse(userService.userIsAppClientDeveloperForApp(newAppClient.getId(), "randomdude@test.com"));

		newAppClient.setAppClientDevelopers(null);
		assertFalse(userService.userIsAppClientDeveloperForApp(newAppClient.getId(), "randomdude@test.com"));
	}

	@Test
	void testDeleteDeveloperFromAllAppClients() {

		DashboardUser newUser = DashboardUser.builder()
				.id(UUID.randomUUID())
				.email("dude@dude.com")
				.privileges(Set.of(appClientDev))
				.build();

		AppClientUser newAppClient = AppClientUser.builder()
				.id(UUID.randomUUID())
				.name("Some App")
				.appClientDevelopers(Sets.newHashSet(newUser))
				.build();

		Mockito.when(repository.findByAppClientDevelopersContaining(Mockito.any()))
				.thenReturn(Lists.newArrayList(newAppClient));

		userService.deleteDeveloperFromAllAppClient(newUser);
		assertTrue(newAppClient.getAppClientDevelopers().isEmpty());
	}
	
	@Test
	void testGetAppClientUsersContainingDeveloperEmail() {
		DashboardUser newUser = DashboardUser.builder()
				.id(UUID.randomUUID())
				.email("dude@dude.com")
				.privileges(Set.of(appClientDev))
				.build();

		AppClientUser newAppClient = AppClientUser.builder()
				.id(UUID.randomUUID())
				.name("Some App")
				.appClientDevelopers(Sets.newHashSet(newUser))
				.build();
		
		List<AppClientUser> appClientUsers = Lists.newArrayList(newAppClient);

		Mockito.when(repository.findByAppClientDevelopersEmailIgnoreCase((Mockito.anyString())))
			.thenReturn(appClientUsers);

		Iterable<AppClientUser> retrievedAppClientUsers = userService.getAppClientUsersContainingDeveloperEmail(newUser.getEmail());
		assertEquals(appClientUsers, retrievedAppClientUsers);
	}

}
