package mil.tron.commonapi.service.utility;

import mil.tron.commonapi.entity.DashboardUser;

public interface DashboardUserUniqueChecksService {
    boolean userEmailIsUnique(DashboardUser user);
}
