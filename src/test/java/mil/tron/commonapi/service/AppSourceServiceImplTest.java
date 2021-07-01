package mil.tron.commonapi.service;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.val;
import mil.tron.commonapi.appgateway.AppSourceInterfaceDefinition;
import mil.tron.commonapi.dto.AppClientUserPrivDto;
import mil.tron.commonapi.dto.DashboardUserDto;
import mil.tron.commonapi.dto.PrivilegeDto;
import mil.tron.commonapi.dto.appsource.AppEndPointPrivDto;
import mil.tron.commonapi.dto.appsource.AppEndpointDto;
import mil.tron.commonapi.dto.appsource.AppSourceDetailsDto;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.appsource.AppEndpoint;
import mil.tron.commonapi.entity.appsource.AppEndpointPriv;
import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.exception.InvalidAppSourcePermissions;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.health.AppSourceHealthIndicator;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import mil.tron.commonapi.repository.appsource.AppEndpointPrivRepository;
import mil.tron.commonapi.repository.appsource.AppEndpointRepository;
import mil.tron.commonapi.repository.appsource.AppSourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
public class AppSourceServiceImplTest {

    @Mock
    private AppSourceRepository appSourceRepository;
    
    @Mock
    private AppEndpointPrivRepository appSourcePrivRepo;
    
    @Mock
    private AppClientUserRespository appClientUserRepo;

    @Mock
    private AppEndpointRepository appEndpointRepo;

    @Mock
	private PrivilegeRepository privilegeRepo;
    
    @Mock
	private DashboardUserService dashboardUserService;

    @Mock
	private DashboardUserRepository dashboardUserRepository;

    @Mock
	private AppGatewayService appGatewayService;

    @Mock
	private HealthContributorRegistry healthContributorRegistry;

    @InjectMocks
    private AppSourceServiceImpl service;

    private static UUID APP_SOURCE_UUID = UUID.randomUUID();
    private static String APP_SOURCE_NAME = "Test App Source";
    private static String APP_SOURCE_ADMIN = "APP_SOURCE_ADMIN";
    private static String DASHBOARD_ADMIN = "DASHBOARD_ADMIN";
    private List<AppSource> entries = new ArrayList<>();
    private Set<AppEndpointPriv> appSourcePrivs = new HashSet<>();
    private AppSource appSource;
    private AppSourceDetailsDto appSourceDetailsDto;
    private List<AppClientUserPrivDto> appClientUserPrivDtos;
    private AppClientUser appClientUser;    
    private Privilege appSourceAdminPriv;
    private Privilege dashboardAdminPriv;
    private Set<Privilege> privileges;
    private AppEndpoint appEndpoint;
    private Set<AppEndpoint> appEndpoints = new HashSet<>();
    private List<AppEndpointDto> appEndpointDtos;
    private ModelMapper mapper = new ModelMapper();

    private Map<String, AppSourceInterfaceDefinition> appDefs = new HashMap<>();

    @BeforeEach
    void setup() {
    	appDefs.put("puckboard",
				AppSourceInterfaceDefinition
						.builder()
						.name("Puckboard")
						.openApiSpecFilename("puckboard.yml")
						.sourceUrl("http://puckboard-api-service.tron-puckboard.svc.cluster.local/puckboard-api/v2")
						.appSourcePath("puckboard")
						.build());

    	privileges = new HashSet<>();
    	privileges.add(
			Privilege
    			.builder()
    			.id(1L)
    			.name("Read")
    			.build()
		);
    	privileges.add(
			Privilege
    			.builder()
    			.id(2L)
    			.name("Write")
    			.build()
		);

    	appSourceAdminPriv = Privilege
					.builder()
					.id(3L)
					.name(APP_SOURCE_ADMIN)
					.build();

		dashboardAdminPriv = Privilege
				.builder()
				.id(4L)
				.name(DASHBOARD_ADMIN)
				.build();


		DashboardUser adminUser = DashboardUser.builder()
				.id(UUID.randomUUID())
				.email("joe@test.com")
				.privileges(Set.of(appSourceAdminPriv))
				.build();
    	
        this.appSource = AppSource
                .builder()
                .id(APP_SOURCE_UUID)
                .name(APP_SOURCE_NAME)
				.appSourceAdmins(Set.of(adminUser))
                .build();
        this.appEndpoint = AppEndpoint
                .builder()
                .id(UUID.randomUUID())
                .appSource(appSource)
                .method(RequestMethod.GET)
                .path("/path")
                .build();
        appClientUser = AppClientUser
                .builder()
                .id(UUID.randomUUID())
                .name("Test App Client")
                .build();
        val appSourcePriv = AppEndpointPriv
                .builder()
                .id(UUID.randomUUID())
                .appSource(appSource)
                .appClientUser(appClientUser)
                .appEndpoint(appEndpoint)
                .build();
        appSourcePrivs.add(
            appSourcePriv
        );
        appEndpoints.add(appEndpoint);
        appSource.setAppPrivs(appSourcePrivs);
        appSource.setAppEndpoints(appEndpoints);
        appClientUser.setAppEndpointPrivs(appSourcePrivs);
        appSourcePrivs.add(appSourcePriv);
        entries.add(appSource);
        
        appClientUserPrivDtos = new ArrayList<>();
        appClientUserPrivDtos.add(
    		AppClientUserPrivDto
        		.builder()
        		.appClientUser(appClientUser.getId())
                .appClientUserName(appClientUser.getName())
        		.appEndpoint(appEndpoint.getId())
                .privilege(appEndpoint.getPath())
        		.build()
		);

        appEndpointDtos = new ArrayList<>();
        appEndpointDtos.add(
            AppEndpointDto
                .builder()
                .path("/path")  
                .requestType(RequestMethod.GET.toString())
                .id(appEndpoint.getId())
                .build()
        );
        
        appSourceDetailsDto = AppSourceDetailsDto
        		.builder()
        		.id(appSource.getId())
        		.name(appSource.getName())
				.appSourceAdminUserEmails(appSource
						.getAppSourceAdmins()
						.stream().map(DashboardUser::getEmail)
						.collect(Collectors.toList()))
        		.appClients(appClientUserPrivDtos)
                .endpoints(appEndpointDtos)
        		.build();
    }

    @Nested
    class Get {
    	@Test
        void getAppSources() {
            Mockito.when(appSourceRepository.findByAvailableAsAppSourceTrue()).thenReturn(entries);
            assertEquals(1, service.getAppSources().size());
        }

        @Test
        void getAppSourceDetails() {
            Mockito.when(appSourceRepository.findById(APP_SOURCE_UUID)).thenReturn(
                    Optional.of(appSource));
            assertEquals(APP_SOURCE_NAME, service.getAppSource(APP_SOURCE_UUID).getName());
        }
        
        @Test
        void getAppSourceDetails_notFound() {
        	Mockito.when(appSourceRepository.findById(APP_SOURCE_UUID)).thenReturn(Optional.ofNullable(null));
        	assertThrows(RecordNotFoundException.class, () -> service.getAppSource(APP_SOURCE_UUID));
        }
		
		@Test
		void getApiSpecResource_ResourcenotFound() {
			appSource.setOpenApiSpecFilename("abcdefg.yml");
			Mockito.when(appSourceRepository.findById(APP_SOURCE_UUID)).thenReturn(Optional.of(appSource));
			assertThrows(RecordNotFoundException.class, () -> service.getApiSpecForAppSource(APP_SOURCE_UUID));
		}

		@Test
		void getApiSpecResource_AppSourcenotFound() {
			Mockito.when(appSourceRepository.findById(APP_SOURCE_UUID)).thenReturn(Optional.empty());
			assertThrows(RecordNotFoundException.class, () -> service.getApiSpecForAppSource(APP_SOURCE_UUID));
		}

		@Test
		void getApiSpecResourceEndpointPriv_ResourcenotFound() {
			appSource.setOpenApiSpecFilename("abcdefg.yml");
			Mockito.when(appSourceRepository.findByAppPrivs_Id(Mockito.any(UUID.class))).thenReturn(Optional.of(appSource));
			assertThrows(RecordNotFoundException.class, () -> service.getApiSpecForAppSourceByEndpointPriv(APP_SOURCE_UUID));
		}

		@Test
		void getApiSpecResourceEndpointPriv_AppSourcenotFound() {
			Mockito.when(appSourceRepository.findByAppPrivs_Id(Mockito.any(UUID.class))).thenReturn(Optional.empty());
			assertThrows(RecordNotFoundException.class, () -> service.getApiSpecForAppSourceByEndpointPriv(APP_SOURCE_UUID));
		}
    }
    
    @Nested
    class Update {
    	@Test
        void idNotMatching() {
        	assertThrows(InvalidRecordUpdateRequest.class, () -> service.updateAppSource(UUID.randomUUID(), appSourceDetailsDto));
        }
        
        @Test
        void idNotExists() {
        	Mockito.when(appSourceRepository.findById(Mockito.any(UUID.class))).thenReturn(Optional.ofNullable(null));
        	
        	assertThrows(RecordNotFoundException.class, () -> service.updateAppSource(appSourceDetailsDto.getId(), appSourceDetailsDto));
        }
        
        @Test
        void nameAlreadyExists() {
        	Mockito.when(appSourceRepository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(appSource));
        	Mockito.when(appSourceRepository.existsByNameIgnoreCase(Mockito.anyString())).thenReturn(true);
        	
        	AppSourceDetailsDto toUpdate = AppSourceDetailsDto
            		.builder()
            		.id(appSource.getId())
            		.name("New Name")
            		.appClients(appClientUserPrivDtos)
            		.build();
        	
        	assertThrows(ResourceAlreadyExistsException.class, () -> service.updateAppSource(toUpdate.getId(), toUpdate));
        }
        
        @Test
        void successUpdate() {
        	Mockito.when(appSourceRepository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(appSource));

        	appClientUserPrivDtos.remove(0);
        	AppSourceDetailsDto toUpdate = AppSourceDetailsDto
            		.builder()
            		.id(appSource.getId())
            		.name(appSource.getName())
            		.appClients(appClientUserPrivDtos)
            		.build();
        	
        	Mockito.when(appSourceRepository.saveAndFlush(Mockito.any())).thenReturn(AppSource.builder().id(toUpdate.getId()).name(toUpdate.getName()).build());
			Mockito.when(healthContributorRegistry.unregisterContributor(Mockito.anyString())).thenReturn(new AppSourceHealthIndicator("", ""));
        	List<AppEndpointPriv> existingPrivs = new ArrayList<>();
        	Mockito.when(appSourcePrivRepo.findAllByAppSource(Mockito.any(AppSource.class))).thenReturn(existingPrivs);
        	
        	AppSourceDetailsDto updated = service.updateAppSource(toUpdate.getId(), toUpdate);
        	
        	assertThat(updated).isEqualTo(toUpdate);
        }

		@Test
        void successUpdateWithEndpoints() {
        	Mockito.when(appSourceRepository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(appSource));

        	appClientUserPrivDtos.remove(0);
        	AppSourceDetailsDto toUpdate = AppSourceDetailsDto
            		.builder()
            		.id(appSource.getId())
            		.name(appSource.getName())
            		.appClients(appClientUserPrivDtos)
					.endpoints(appEndpointDtos)
            		.build();
        	
        	Mockito.when(appSourceRepository.saveAndFlush(Mockito.any())).thenReturn(AppSource.builder().id(toUpdate.getId()).name(toUpdate.getName()).build());
			Mockito.when(healthContributorRegistry.unregisterContributor(Mockito.anyString())).thenReturn(new AppSourceHealthIndicator("", ""));
        	List<AppEndpointPriv> existingPrivs = new ArrayList<>();
        	Mockito.when(appSourcePrivRepo.findAllByAppSource(Mockito.any(AppSource.class))).thenReturn(existingPrivs);

			Mockito.when(appEndpointRepo.findAllByAppSource(appSource)).thenReturn(appEndpoints.stream().collect(Collectors.toList()));
        	
        	AppSourceDetailsDto updated = service.updateAppSource(toUpdate.getId(), toUpdate);
        	
        	assertThat(updated).isEqualTo(toUpdate);
        }
    }
    
    @Nested
    class Delete {
    	@Test
    	void notExists() {
    		Mockito.when(appSourceRepository.findById(Mockito.any(UUID.class))).thenReturn(Optional.ofNullable(null));
    		
    		assertThrows(RecordNotFoundException.class, () -> service.deleteAppSource(appSource.getId()));
    	}
    	
    	@Test
    	void successDelete() {
    		Mockito.when(appSourceRepository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(appSource));
			Mockito.when(appSourceRepository.findAppSourcesByAppSourceAdminsContaining(Mockito.any()))
					.thenReturn(Lists.newArrayList(appSource));
			Mockito.when(appSourceRepository.saveAndFlush(Mockito.any()))
					.thenReturn(appSource);

    		AppSourceDetailsDto removed = service.deleteAppSource(appSource.getId());

			assertThat(appSourceDetailsDto.getAppSourceAdminUserEmails()).size().isGreaterThan(0);
    		assertThat(removed.getId()).isEqualTo(appSourceDetailsDto.getId());
			assertThat(removed.getAppSourcePath()).isNull();
			
    		// admins should be of length 0
    		assertThat(removed.getAppSourceAdminUserEmails().size()).isEqualTo(0);
    	}

		@Test
    	void successDeleteWhenAppClientExists() {
    		Mockito.when(appSourceRepository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(appSource));
			Mockito.when(appSourceRepository.findAppSourcesByAppSourceAdminsContaining(Mockito.any()))
					.thenReturn(Lists.newArrayList(appSource));
			Mockito.when(appSourceRepository.saveAndFlush(Mockito.any()))
					.thenReturn(appSource);
			Mockito.when(appClientUserRepo.existsByIdAndAvailableAsAppClientTrue(Mockito.any(UUID.class))).thenReturn(true);
			Mockito.when(appSourceRepository.save(Mockito.any()))
				.thenReturn(AppSource.builder()
					.id(appSource.getId())
					.availableAsAppSource(false)
					.name(appSource.getName())
					.build());

    		AppSourceDetailsDto removed = service.deleteAppSource(appSource.getId());

			assertThat(appSourceDetailsDto.getAppSourceAdminUserEmails()).size().isGreaterThan(0);
    		assertThat(removed.getId()).isEqualTo(appSourceDetailsDto.getId());
			assertThat(removed.getAppSourcePath()).isNull();

    		// admins should be of length 0
    		assertThat(removed.getAppSourceAdminUserEmails().size()).isEqualTo(0);
    	}
    }
    
    @Test
    void testCreateAppSource() {

    	AppSource appSource = AppSource.builder()
				.id(appSourceDetailsDto.getId())
				.name(appSourceDetailsDto.getName())
				.availableAsAppSource(true)
				.appSourceAdmins(null)
				.build();

    	Mockito.when(appSourceRepository.saveAndFlush(Mockito.any()))
                .thenReturn(appSource);
    	
    	List<AppEndpointPriv> existingPrivs = new ArrayList<>();
    	Mockito.when(appSourcePrivRepo.findAllByAppSource(Mockito.any(AppSource.class))).thenReturn(existingPrivs);
		Mockito.doNothing().when(healthContributorRegistry).registerContributor(Mockito.anyString(), Mockito.any(AppSourceHealthIndicator.class));
		Mockito.when(healthContributorRegistry.unregisterContributor(Mockito.anyString())).thenReturn(new AppSourceHealthIndicator("", ""));
		Mockito.when(appGatewayService.getDefMap()).thenReturn(appDefs);
        Mockito.when(appEndpointRepo.findAllByAppSource(Mockito.any(AppSource.class))).thenReturn(new ArrayList<>());
    	Mockito.when(appClientUserRepo.findById(Mockito.any())).thenReturn(Optional.of(appClientUser));
    	Mockito.when(privilegeRepo.findByName(APP_SOURCE_ADMIN)).thenReturn(Optional.of(appSourceAdminPriv));
    	Mockito.when(appSourceRepository.findByNameIgnoreCase(Mockito.anyString()))
				.thenReturn(Optional.ofNullable(appSource));
    	AppSourceDetailsDto created = service.createAppSource(appSourceDetailsDto);

    	appSourceDetailsDto.setId(created.getId());
    	assertThat(created).isEqualTo(appSourceDetailsDto);

    	appSourceDetailsDto.setReportStatus(true);
		appSource.setName("puckboard");
    	appSource.setAppSourceAdmins(null);
    	appSource.setAppSourcePath("puckboard");
    	assertTrue(service.createAppSource(appSourceDetailsDto).isReportStatus());
    }

	@Test
	void createAppSourceOnTopOfExistingAppClient() {
		AppSource appSource = AppSource.builder()
			.id(appSourceDetailsDto.getId())
			.name(appSourceDetailsDto.getName())
			.build();
		Mockito.when(appSourceRepository.saveAndFlush(Mockito.any())).thenReturn(appSource);

		Mockito.when(appSourceRepository.findByNameIgnoreCase(Mockito.any())).thenReturn(Optional.of(appSource));
		Mockito.when(healthContributorRegistry.unregisterContributor(Mockito.anyString())).thenReturn(new AppSourceHealthIndicator("", ""));
    	List<AppEndpointPriv> existingPrivs = new ArrayList<>();
    	Mockito.when(appSourcePrivRepo.findAllByAppSource(Mockito.any(AppSource.class))).thenReturn(existingPrivs);

        Mockito.when(appEndpointRepo.findAllByAppSource(Mockito.any(AppSource.class))).thenReturn(new ArrayList<>());
    	
    	Mockito.when(appClientUserRepo.findById(Mockito.any())).thenReturn(Optional.of(appClientUser));
    	Mockito.when(privilegeRepo.findByName(APP_SOURCE_ADMIN)).thenReturn(Optional.of(appSourceAdminPriv));
    	AppSourceDetailsDto created = service.createAppSource(appSourceDetailsDto);

    	appSourceDetailsDto.setId(created.getId());

    	assertThat(created).isEqualTo(appSourceDetailsDto);
	}

	@Test
	void testAddAppSourceAdmin() {

    	AppSource newAppSource = AppSource.builder()
				.id(UUID.randomUUID())
				.name("Some App")
				.appSourcePath("")
				.openApiSpecFilename("")
				.appSourceAdmins(new HashSet<>())
				.appPrivs(new HashSet<>())
				.build();

    	DashboardUser newUser = DashboardUser.builder()
				.id(UUID.randomUUID())
				.email("dude@dude.com")
				.privileges(Set.of(appSourceAdminPriv))
				.build();

		DashboardUser newUser2 = DashboardUser.builder()
				.id(UUID.randomUUID())
				.email("dude2@dude.com")
				.privileges(new HashSet<>())
				.build();

		Mockito.when(appSourceRepository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(newAppSource));
		Mockito.when(appSourceRepository.saveAndFlush(Mockito.any())).thenReturn(newAppSource);
		Mockito.when(privilegeRepo.findByName(Mockito.any())).thenReturn(Optional.of(appSourceAdminPriv));
		Mockito.when(dashboardUserRepository.findByEmailIgnoreCase(Mockito.any()))
				.thenReturn(Optional.of(newUser))
				.thenReturn(Optional.empty());

		Mockito.when(dashboardUserService.createDashboardUserDto(Mockito.any())).thenReturn(DashboardUserDto
				.builder()
				.id(newUser2.getId())
				.privileges(Lists
						.newArrayList(newUser2
								.getPrivileges()
									.stream()
									.map(item -> mapper.map(item, PrivilegeDto.class))
									.collect(Collectors.toList())))
				.email(newUser2.getEmail())
				.build());
		Mockito.when(dashboardUserService.convertToEntity(Mockito.any())).thenReturn(newUser2);

		// add existing user
		service.addAppSourceAdmin(newAppSource.getId(), newUser.getEmail());
		assertThat(newAppSource.getAppSourceAdmins().size()).isEqualTo(1);

		// add brand new user
		service.addAppSourceAdmin(newAppSource.getId(), newUser2.getEmail());
		assertThat(newAppSource.getAppSourceAdmins().size()).isEqualTo(2);

	}

	@Test
	void testRemoveAdminFromAppSource() {

		DashboardUser newUser = DashboardUser.builder()
				.id(UUID.randomUUID())
				.email("dude@dude.com")
				.privileges(Set.of(appSourceAdminPriv, dashboardAdminPriv))
				.build();

		DashboardUser newUser2 = DashboardUser.builder()
				.id(UUID.randomUUID())
				.email("dude2@dude.com")
				.privileges(new HashSet<>())
				.build();

		AppSource newAppSource = AppSource.builder()
				.id(UUID.randomUUID())
				.name("Some App")
				.appSourcePath("")
				.openApiSpecFilename("")
				.appSourceAdmins(Sets.newHashSet(newUser, newUser2))
				.appPrivs(new HashSet<>())
				.build();

		AppSource someOtherApp = AppSource.builder()
				.id(UUID.randomUUID())
				.name("Another App")
				.appSourcePath("")
				.openApiSpecFilename("")
				.appSourceAdmins(Sets.newHashSet(newUser))
				.appPrivs(new HashSet<>())
				.build();

		Mockito.when(appSourceRepository.findById(Mockito.any(UUID.class)))
				.thenReturn(Optional.of(newAppSource));
		Mockito.when(appSourceRepository.saveAndFlush(Mockito.any()))
				.thenReturn(newAppSource);

		Mockito.when(appSourceRepository.findAppSourcesByAppSourceAdminsContaining(newUser2))
				.thenReturn(Lists.newArrayList());

		Mockito.doNothing().when(dashboardUserService).deleteDashboardUser(Mockito.any());
		Mockito.when(dashboardUserRepository.save(Mockito.any())).then(returnsFirstArg());

		// newUser2 should only be in newAppSource, so upon removal, they'll be purged completely
		service.removeAdminFromAppSource(newAppSource.getId(), newUser2.getEmail());
		Mockito.verify(dashboardUserService, times(1))
				.deleteDashboardUser(newUser2.getId());

		// newUser should only loose their APP_SOURCE_ADMIN cred
		service.removeAdminFromAppSource(someOtherApp.getId(), newUser.getEmail());
		service.removeAdminFromAppSource(newAppSource.getId(), newUser.getEmail());
		Mockito.verify(dashboardUserRepository, times(1))
				.save(Mockito.any());
	}

	@Test
	void testUserIsAdminForAppSource() {
		DashboardUser newUser = DashboardUser.builder()
				.id(UUID.randomUUID())
				.email("dude@dude.com")
				.privileges(Set.of(appSourceAdminPriv))
				.build();

		AppSource newAppSource = AppSource.builder()
				.id(UUID.randomUUID())
				.name("Some App")
				.appSourcePath("")
				.openApiSpecFilename("")
				.appSourceAdmins(Sets.newHashSet(newUser))
				.appPrivs(new HashSet<>())
				.build();

		Mockito.when(appSourceRepository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(newAppSource));

		assertTrue(service.userIsAdminForAppSource(newAppSource.getId(), newUser.getEmail()));
		assertFalse(service.userIsAdminForAppSource(newAppSource.getId(), "randomdude@test.com"));
		
		Mockito.when(appSourceRepository.findById(Mockito.any(UUID.class))).thenReturn(Optional.ofNullable(null));
		assertThrows(RecordNotFoundException.class, () -> service.userIsAdminForAppSource(newAppSource.getId(), "randomdude@test.com"));
	}
	
	@Test
	void testUserIsAdminForAppSourceByEndpoint() {
		DashboardUser newUser = DashboardUser.builder()
				.id(UUID.randomUUID())
				.email("dude@dude.com")
				.privileges(Set.of(appSourceAdminPriv))
				.build();

		AppSource newAppSource = AppSource.builder()
				.id(UUID.randomUUID())
				.name("Some App")
				.appSourcePath("")
				.openApiSpecFilename("")
				.appSourceAdmins(Sets.newHashSet(newUser))
				.appPrivs(new HashSet<>())
				.build();
		
		AppEndpoint endpoint = AppEndpoint.builder()
				.appSource(newAppSource)
				.id(UUID.randomUUID())
				.path("/test/path")
				.method(RequestMethod.DELETE)
				.build();
		
		newAppSource.setAppEndpoints(Sets.newHashSet(endpoint));

		Mockito.when(appEndpointRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(endpoint));

		assertTrue(service.userIsAdminForAppSourceByEndpoint(endpoint.getId(), newUser.getEmail()));
		assertFalse(service.userIsAdminForAppSourceByEndpoint(endpoint.getId(), "randomdude@test.com"));
		
		Mockito.when(appEndpointRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.ofNullable(null));
		assertThrows(RecordNotFoundException.class, () -> service.userIsAdminForAppSourceByEndpoint(endpoint.getId(), "randomdude@test.com"));
	}

	@Test
	void testDeleteAllAppClientPrivs() {
    	AppSource app = AppSource.builder()
				.id(UUID.randomUUID())
				.name("Test")
				.appSourceAdmins(new HashSet<>())
				.appSourcePath("test")
				.appPrivs(Set.of(AppEndpointPriv.builder()
						.id(UUID.randomUUID())
						.appClientUser(AppClientUser.builder()
								.id(UUID.randomUUID())
								.name("puckboard")
								.build())
						.appEndpoint(AppEndpoint.builder()
								.id(UUID.randomUUID())
								.method(RequestMethod.GET)
								.path("/blah")
								.build())
						.build()))
				.build();

    	Mockito.when(appSourceRepository.findById(Mockito.any(UUID.class)))
				.thenReturn(Optional.empty())
				.thenReturn(Optional.of(app));

    	Mockito.doNothing().when(appSourcePrivRepo).delete(Mockito.any(AppEndpointPriv.class));
    	Mockito.when(appSourceRepository.saveAndFlush(Mockito.any())).then(returnsFirstArg());

    	assertThrows(RecordNotFoundException.class, () -> service.deleteAllAppClientPrivs(app.getId()));
    	assertEquals(app.getId(), service.deleteAllAppClientPrivs(app.getId()).getId());
	}

	@Test
	void testAddEndPointPrivilege() {
    	AppEndpoint endPoint = AppEndpoint.builder()
				.id(UUID.randomUUID())
				.method(RequestMethod.GET)
				.path("/blah")
				.build();

    	AppClientUser client = AppClientUser.builder()
				.id(UUID.randomUUID())
				.name("puckboard")
				.build();

		AppSource app = AppSource.builder()
				.id(UUID.randomUUID())
				.name("Test")
				.appSourceAdmins(new HashSet<>())
				.appSourcePath("test")
				.appPrivs(Set.of(AppEndpointPriv.builder()
						.id(UUID.randomUUID())
						.appClientUser(client)
						.appEndpoint(endPoint)
						.build()))
				.build();

		AppEndPointPrivDto dto = AppEndPointPrivDto
				.builder()
				.appSourceId(app.getId())
				.appEndpointId(endPoint.getId())
				.appClientUserId(client.getId())
				.build();

		Mockito.when(appSourceRepository.findById(Mockito.any()))
				.thenReturn(Optional.empty())
				.thenReturn(Optional.of(app));

		Mockito.when(appEndpointRepo.findById(Mockito.any()))
				.thenReturn(Optional.empty())
				.thenReturn(Optional.of(endPoint));

		Mockito.when(appClientUserRepo.findById(Mockito.any()))
				.thenReturn(Optional.empty())
				.thenReturn(Optional.of(client));

		Mockito.when(appSourcePrivRepo.existsByAppSourceEqualsAndAppClientUserEqualsAndAppEndpointEquals(
			app, client, endPoint))
				.thenReturn(true)
				.thenReturn(false);

		Mockito.when(appSourcePrivRepo.saveAndFlush(Mockito.any()))
				.thenReturn(AppEndpointPriv
						.builder()
						.appSource(app)
						.appEndpoint(endPoint)
						.appClientUser(client)
						.build());

		Mockito.when(appSourceRepository.saveAndFlush(Mockito.any())).then(returnsFirstArg());

		assertThrows(RecordNotFoundException.class, () -> service.addEndPointPrivilege(dto));
		assertThrows(RecordNotFoundException.class, () -> service.addEndPointPrivilege(dto));
		assertThrows(RecordNotFoundException.class, () -> service.addEndPointPrivilege(dto));
		assertThrows(ResourceAlreadyExistsException.class, () -> service.addEndPointPrivilege(dto));
		assertEquals(app.getId(), service.addEndPointPrivilege(dto).getId());
    }

    @Test
	void testRemoveEndpointPrivilege() {
		AppEndpoint endPoint = AppEndpoint.builder()
				.id(UUID.randomUUID())
				.method(RequestMethod.GET)
				.path("/blah")
				.build();

		AppClientUser client = AppClientUser.builder()
				.id(UUID.randomUUID())
				.name("puckboard")
				.build();

		AppSource app = AppSource.builder()
				.id(UUID.randomUUID())
				.name("Test")
				.appSourceAdmins(new HashSet<>())
				.appSourcePath("test")
				.appPrivs(Set.of(AppEndpointPriv.builder()
						.id(UUID.randomUUID())
						.appClientUser(client)
						.appEndpoint(endPoint)
						.build()))
				.build();

		AppEndpointPriv priv = AppEndpointPriv
				.builder()
				.appSource(app)
				.appEndpoint(endPoint)
				.appClientUser(client)
				.build();

		Mockito.when(appSourceRepository.findById(Mockito.any()))
				.thenReturn(Optional.empty())
				.thenReturn(Optional.of(app));

		Mockito.when(appSourcePrivRepo.findById(Mockito.any()))
				.thenReturn(Optional.empty())
				.thenReturn(Optional.of(AppEndpointPriv
						.builder()
						.appSource(appSource)
						.appEndpoint(endPoint)
						.appClientUser(client)
						.build()))
				.thenReturn(Optional.of(priv));

		Mockito.doNothing().when(appSourcePrivRepo).deleteById(Mockito.any());

		Mockito.when(appSourceRepository.saveAndFlush(Mockito.any())).then(returnsFirstArg());

		assertThrows(RecordNotFoundException.class, () -> service.removeEndPointPrivilege(app.getId(), priv.getId()));
		assertThrows(RecordNotFoundException.class, () -> service.removeEndPointPrivilege(app.getId(), priv.getId()));
		assertThrows(InvalidAppSourcePermissions.class, () -> service.removeEndPointPrivilege(app.getId(), UUID.randomUUID()));
		assertEquals(app.getId(), service.removeEndPointPrivilege(app.getId(), priv.getId()).getId());
	}

	@Test
	void testDeleteAdminFromAllAppSources() {

		DashboardUser newUser = DashboardUser.builder()
				.id(UUID.randomUUID())
				.email("dude@dude.com")
				.privileges(Set.of(appSourceAdminPriv))
				.build();

		AppSource newAppSource = AppSource.builder()
				.id(UUID.randomUUID())
				.name("Some App")
				.appSourcePath("")
				.openApiSpecFilename("")
				.appSourceAdmins(Sets.newHashSet(newUser))
				.appPrivs(new HashSet<>())
				.build();

		Mockito.when(appSourceRepository.findAppSourcesByAppSourceAdminsContaining(Mockito.any()))
				.thenReturn(Lists.newArrayList(newAppSource));

		service.deleteAdminFromAllAppSources(newUser);
		assertTrue(newAppSource.getAppSourceAdmins().isEmpty());
	}
}
