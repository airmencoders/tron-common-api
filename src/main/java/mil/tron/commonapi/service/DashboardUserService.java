package mil.tron.commonapi.service;

import mil.tron.commonapi.entity.DashboardUser;

public interface DashboardUserService {
    // how to set up a user who has not hit the dashboard yet?  email?  Is it assumed that the user is authenticated already
    DashboardUser createDashboardUser(DashboardUser dashboardUser);
//    DashboardUser updateDashboardUser(UUID id, DashboardUser dashboardUser);
//    void removeDashboardUser(UUID id);
//    DashboardUser getDashboardUser(UUID id);

    Iterable<DashboardUser> getDashboardUsers();
}
