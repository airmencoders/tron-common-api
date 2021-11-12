package mil.tron.commonapi.service;

import mil.tron.commonapi.dto.DashboardUserDto;
import mil.tron.commonapi.entity.DashboardUser;

import java.util.UUID;

import org.springframework.lang.Nullable;

public interface DashboardUserService {
    DashboardUserDto createDashboardUserDto(DashboardUserDto dashboardUserDto);
    DashboardUser createDashboardUser(DashboardUserDto dashboardUserDto);
    DashboardUser createDashboardUserOrReturnExisting(String email);
    DashboardUserDto updateDashboardUserDto(UUID id, DashboardUserDto dashboardUserDto);
    void deleteDashboardUser(UUID id);
    DashboardUserDto getDashboardUserDto(UUID id);
    Iterable<DashboardUserDto> getAllDashboardUsersDto();
    @Nullable
    DashboardUser getDashboardUserByEmail(String email);
    @Nullable
    DashboardUser getDashboardUserByEmailAsLower(String email);
    DashboardUserDto getSelf(String email);
    // conversions
    DashboardUserDto convertToDto(DashboardUser user);
    DashboardUser convertToEntity(DashboardUserDto dto);

}
