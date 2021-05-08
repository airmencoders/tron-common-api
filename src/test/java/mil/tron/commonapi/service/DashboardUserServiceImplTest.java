package mil.tron.commonapi.service;

import mil.tron.commonapi.dto.DashboardUserDto;

import mil.tron.commonapi.dto.PrivilegeDto;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import mil.tron.commonapi.service.utility.DashboardUserUniqueChecksServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
public class DashboardUserServiceImplTest {
    @Mock
    private DashboardUserRepository dashboardUserRepo;

    @Mock
    private DashboardUserUniqueChecksServiceImpl uniqueChecksService;

    @Mock
    private AppSourceServiceImpl appSourceService;

    @Mock
    private AppClientUserServiceImpl appClientUserService;

    @Mock
    private PrivilegeRepository privilegeRepository;

    @InjectMocks
    private DashboardUserServiceImpl dashboardUserService;

    private DashboardUser testDashboardUser;
    private DashboardUserDto testDashboardUserDto;
    private ModelMapper mapper = new ModelMapper();

    @BeforeEach
    public void beforeEaschSetup() {
        Privilege priv = new Privilege((long)3,"DASHBOARD_ADMIN");
        HashSet<Privilege> privileges = new HashSet<>();
        privileges.add(priv);

        testDashboardUser = DashboardUser.builder()
                .email("test@test.com")
                .privileges(privileges)
                .build();
        testDashboardUserDto = dashboardUserService.convertToDto(testDashboardUser);
    }

    @Nested
    class CreateDashboardUserTest {
        @Test
        void successfulCreate() {
            // Test successful save
            Mockito.when(dashboardUserRepo.save(Mockito.any(DashboardUser.class))).thenReturn(testDashboardUser);
            Mockito.when(dashboardUserRepo.existsById(Mockito.any(UUID.class))).thenReturn(false);
            Mockito.when(uniqueChecksService.userEmailIsUnique(Mockito.any(DashboardUser.class))).thenReturn(true);
            Mockito.when(privilegeRepository.findByName(Mockito.any()))
                    .thenReturn(Optional.of(Privilege.builder()
                            .name("DASHBOARD_USER")
                            .id(1L)
                            .build()))
                    .thenReturn(Optional.empty());
            DashboardUserDto createdDashboardUserDto = dashboardUserService.createDashboardUserDto(testDashboardUserDto);
            assertThat(createdDashboardUserDto.getId()).isEqualTo(testDashboardUser.getId());
            assertThrows(RecordNotFoundException.class, () -> dashboardUserService.createDashboardUserDto(testDashboardUserDto));
        }

        @Test
        void idAlreadyExists() {
            // Test id already exists
            Mockito.when(uniqueChecksService.userEmailIsUnique(Mockito.any(DashboardUser.class))).thenReturn(true);
            Mockito.when(dashboardUserRepo.existsById(Mockito.any(UUID.class))).thenReturn(true);
            Mockito.when(privilegeRepository.findByName(Mockito.any()))
                    .thenReturn(Optional.of(Privilege.builder()
                        .name("DASHBOARD_USER")
                        .id(1L)
                        .build()));

            assertThrows(ResourceAlreadyExistsException.class, () -> dashboardUserService.createDashboardUserDto(testDashboardUserDto));
        }

        @Test
        void emailAlreadyExists() {
            // Test email already exists
            DashboardUser userWithEmail = new DashboardUser();
            userWithEmail.setEmail(testDashboardUser.getEmail());
            userWithEmail.setPrivileges(testDashboardUser.getPrivileges());

            Mockito.when(uniqueChecksService.userEmailIsUnique(Mockito.any(DashboardUser.class))).thenReturn(false);
            assertThatExceptionOfType(ResourceAlreadyExistsException.class).isThrownBy(() -> {
                dashboardUserService.createDashboardUserDto(testDashboardUserDto);
            });
        }
    }

    @Nested
    class UpdateDashboardUserTest {
        @Test
        void idsNotMatching() {
            // Test id not matching person id
            assertThrows(InvalidRecordUpdateRequest.class, () -> dashboardUserService.updateDashboardUserDto(UUID.randomUUID(), testDashboardUserDto));
        }

        @Test
        void idNotExist() {
            // Test id not exist
            Mockito.when(dashboardUserRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.ofNullable(null));
            assertThrows(RecordNotFoundException.class, () -> dashboardUserService.updateDashboardUserDto(testDashboardUserDto.getId(), testDashboardUserDto));
        }

        @Test
        void emailAlreadyExists() {
            // Test updating email to one that already exists in database
            DashboardUserDto userWithEmail = new DashboardUserDto();
            userWithEmail.setEmail(testDashboardUser.getEmail());
            userWithEmail.setPrivileges(new ArrayList<>(testDashboardUser
                    .getPrivileges()
                    .stream()
                    .map(item -> mapper.map(item, PrivilegeDto.class))
                    .collect(Collectors.toList())));
            UUID testId = userWithEmail.getId();

            DashboardUserDto existingUserWithEmail = new DashboardUserDto();
            existingUserWithEmail.setEmail(testDashboardUser.getEmail());
            existingUserWithEmail.setPrivileges(new ArrayList<>(testDashboardUser
                    .getPrivileges()
                    .stream()
                    .map(item -> mapper.map(item, PrivilegeDto.class))
                    .collect(Collectors.toList())));

            Mockito.when(dashboardUserRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(testDashboardUser));
            Mockito.when(uniqueChecksService.userEmailIsUnique(Mockito.any(DashboardUser.class))).thenReturn(false);
            assertThatExceptionOfType(InvalidRecordUpdateRequest.class).isThrownBy(() -> {
                dashboardUserService.updateDashboardUserDto(testId, userWithEmail);
            });
        }

        @Test
        void successfulUpdate() {
            // Successful update
//            Mockito.when(rankRepository.findByAbbreviationAndBranchType(Mockito.any(), Mockito.any())).thenReturn(Optional.of(testPerson.getRank()));
            Mockito.when(dashboardUserRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(testDashboardUser));
            Mockito.when(uniqueChecksService.userEmailIsUnique(Mockito.any(DashboardUser.class))).thenReturn(true);
            Mockito.when(dashboardUserRepo.save(Mockito.any(DashboardUser.class))).thenReturn(testDashboardUser);
            DashboardUserDto updatedDashboardUser = dashboardUserService.updateDashboardUserDto(testDashboardUserDto.getId(), testDashboardUserDto);
            assertThat(updatedDashboardUser.getId()).isEqualTo(testDashboardUser.getId());
        }
    }

    @Test
    void deleteDashboardUserTest() {
        Mockito.when(dashboardUserRepo.findById(Mockito.any(UUID.class)))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(testDashboardUser));
        Mockito.doNothing().when(appSourceService).deleteAdminFromAllAppSources(Mockito.any());
        Mockito.doNothing().when(appClientUserService).deleteDeveloperFromAllAppClient(Mockito.any());

        assertThrows(RecordNotFoundException.class, () -> dashboardUserService.deleteDashboardUser(testDashboardUser.getId()));
        dashboardUserService.deleteDashboardUser(testDashboardUser.getId());
        Mockito.verify(dashboardUserRepo, Mockito.times(1)).delete(testDashboardUser);
    }

    @Test
    void getAllDashboardUsersTest() {
        Mockito.when(dashboardUserRepo.findAll()).thenReturn(Arrays.asList(testDashboardUser));
        Iterable<DashboardUserDto> dashboardUsersDto = dashboardUserService.getAllDashboardUsersDto();
        assertThat(dashboardUsersDto).hasSize(1);
    }

    @Test
    void getDashboardUserTest() {
        // Test person exists
        Mockito.when(dashboardUserRepo.findById(testDashboardUser.getId())).thenReturn(Optional.of(testDashboardUser));
        DashboardUserDto retrievedDashboardUserDto = dashboardUserService.getDashboardUserDto(testDashboardUserDto.getId());
        assertThat(retrievedDashboardUserDto).isEqualTo(testDashboardUserDto);

        // Test person not exists
        Mockito.when(dashboardUserRepo.findById(testDashboardUserDto.getId())).thenReturn(Optional.ofNullable(null));
        assertThrows(RecordNotFoundException.class, () -> dashboardUserService.getDashboardUserDto(testDashboardUserDto.getId()));
    }
    
    @Test
    void getSelfTestExists() {
    	Mockito.when(dashboardUserRepo.findByEmailIgnoreCase(Mockito.anyString())).thenReturn(Optional.of(testDashboardUser));
    	
    	DashboardUserDto retrievedDashboardUserDto = dashboardUserService.getSelf(testDashboardUserDto.getEmail());
    	assertThat(retrievedDashboardUserDto).isEqualTo(testDashboardUserDto);
    }
    
    @Test
    void getSelfTestNotExists() {
    	Mockito.when(dashboardUserRepo.findByEmailIgnoreCase(Mockito.anyString())).thenThrow(new UsernameNotFoundException("Not found"));
    	
    	assertThrows(UsernameNotFoundException.class, () -> dashboardUserService.getSelf(testDashboardUserDto.getEmail()));
    }
}
