package mil.tron.commonapi.service;

import mil.tron.commonapi.dto.DashboardUserDto;

import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.service.utility.DashboardUserUniqueChecksServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
public class DashboardUserServiceImplTest {
    @Mock
    private DashboardUserRepository dashboardUserRepo;

    @Mock
    private DashboardUserUniqueChecksServiceImpl uniqueChecksService;

    @InjectMocks
    private DashboardUserServiceImpl dashboardUserService;

    private DashboardUser testDashboardUser;
    private DashboardUserDto testDashboardUserDto;

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
            Mockito.when(uniqueChecksService.UserEmailIsUnique(Mockito.any(DashboardUser.class))).thenReturn(true);
            DashboardUserDto createdDashboardUserDto = dashboardUserService.createDashboardUserDto(testDashboardUserDto);
            assertThat(createdDashboardUserDto.getId()).isEqualTo(testDashboardUser.getId());
        }

        @Test
        void idAlreadyExists() {
            // Test id already exists
            Mockito.when(uniqueChecksService.UserEmailIsUnique(Mockito.any(DashboardUser.class))).thenReturn(true);
            Mockito.when(dashboardUserRepo.existsById(Mockito.any(UUID.class))).thenReturn(true);
            assertThrows(ResourceAlreadyExistsException.class, () -> dashboardUserService.createDashboardUserDto(testDashboardUserDto));
        }

        @Test
        void emailAlreadyExists() {
            // Test email already exists
            DashboardUser userWithEmail = new DashboardUser();
            userWithEmail.setEmail(testDashboardUser.getEmail());
            userWithEmail.setPrivileges(testDashboardUser.getPrivileges());

            Mockito.when(uniqueChecksService.UserEmailIsUnique(Mockito.any(DashboardUser.class))).thenReturn(false);
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
            userWithEmail.setPrivileges(testDashboardUser.getPrivileges());
            UUID testId = userWithEmail.getId();

            DashboardUserDto existingUserWithEmail = new DashboardUserDto();
            existingUserWithEmail.setEmail(testDashboardUser.getEmail());
            existingUserWithEmail.setPrivileges(testDashboardUser.getPrivileges());

            Mockito.when(dashboardUserRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(testDashboardUser));
            Mockito.when(uniqueChecksService.UserEmailIsUnique(Mockito.any(DashboardUser.class))).thenReturn(false);
            assertThatExceptionOfType(InvalidRecordUpdateRequest.class).isThrownBy(() -> {
                dashboardUserService.updateDashboardUserDto(testId, userWithEmail);
            });
        }

        @Test
        void successfulUpdate() {
            // Successful update
//            Mockito.when(rankRepository.findByAbbreviationAndBranchType(Mockito.any(), Mockito.any())).thenReturn(Optional.of(testPerson.getRank()));
            Mockito.when(dashboardUserRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(testDashboardUser));
            Mockito.when(uniqueChecksService.UserEmailIsUnique(Mockito.any(DashboardUser.class))).thenReturn(true);
            Mockito.when(dashboardUserRepo.save(Mockito.any(DashboardUser.class))).thenReturn(testDashboardUser);
            DashboardUserDto updatedDashboardUser = dashboardUserService.updateDashboardUserDto(testDashboardUserDto.getId(), testDashboardUserDto);
            assertThat(updatedDashboardUser.getId()).isEqualTo(testDashboardUser.getId());
        }
    }

    @Test
    void deleteDashboardUserTest() {
        assertThrows(RecordNotFoundException.class, () -> dashboardUserService.deleteDashboardUser(testDashboardUser.getId()));

        Mockito.when(dashboardUserRepo.existsById(Mockito.any(UUID.class))).thenReturn(true);
        dashboardUserService.deleteDashboardUser(testDashboardUser.getId());
        Mockito.verify(dashboardUserRepo, Mockito.times(1)).deleteById(testDashboardUser.getId());
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
}
