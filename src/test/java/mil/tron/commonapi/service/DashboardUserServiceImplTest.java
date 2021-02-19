package mil.tron.commonapi.service;

import mil.tron.commonapi.dto.DashboardUserDto;

import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.service.utility.DashboardUserUniqueChecksServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

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
