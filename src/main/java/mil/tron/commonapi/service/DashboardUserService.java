package mil.tron.commonapi.service;

import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.dto.DashboardUserDto;

import java.util.UUID;

public interface DashboardUserService {
    // how to set up a user who has not hit the dashboard yet?  email?  Is it assumed that the user is authenticated already
    DashboardUserDto createDashboardUser(DashboardUserDto dashboardUserDto);
//    DashboardUser updateDashboardUser(UUID id, DashboardUser dashboardUser);
//    void removeDashboardUser(UUID id);
//    DashboardUser getDashboardUser(UUID Id);
    DashboardUserDto getDashboardUserDto(UUID Id);

    Iterable<DashboardUserDto> getAllDashboardUsers();

    DashboardUserDto convertToDto(DashboardUser user);
    DashboardUser convertToEntity(DashboardUserDto dto);
}
