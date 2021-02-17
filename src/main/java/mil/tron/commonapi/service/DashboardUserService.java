package mil.tron.commonapi.service;

import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.dto.DashboardUserDto;

import java.util.UUID;

public interface DashboardUserService {
    DashboardUserDto createDashboardUser(DashboardUserDto dashboardUserDto);
    DashboardUserDto updateDashboardUser(UUID id, DashboardUserDto dashboardUserDto);
    void deleteDashboardUser(UUID id);
    DashboardUserDto getDashboardUserDto(UUID Id);
    Iterable<DashboardUserDto> getAllDashboardUsers();

    DashboardUserDto convertToDto(DashboardUser user);
    DashboardUser convertToEntity(DashboardUserDto dto);
}
