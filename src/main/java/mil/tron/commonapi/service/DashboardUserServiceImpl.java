package mil.tron.commonapi.service;

import lombok.val;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import mil.tron.commonapi.service.utility.DashboardUserUniqueChecksService;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DashboardUserServiceImpl implements DashboardUserService {

    private DashboardUserRepository dashboardUserRepository;
    private DashboardUserUniqueChecksService userChecksService;
    private PrivilegeRepository privilegeRepository;

    public DashboardUserServiceImpl(DashboardUserRepository dashboardUserRepository,
                                    DashboardUserUniqueChecksService dashboardUserUniqueChecksService,
                                    PrivilegeRepository privilegeRepository) {
        this.dashboardUserRepository = dashboardUserRepository;
        this.userChecksService = dashboardUserUniqueChecksService;
        this.privilegeRepository = privilegeRepository;
    }

    @Override
    public DashboardUser createDashboardUser(DashboardUser dashboardUser) {
        if (dashboardUser.getId() == null) {
            // we have to generate an ID manually since we're not using normal
            dashboardUser.setId(UUID.randomUUID());
        }

        if (!userChecksService.UserEmailIsUnique(dashboardUser))
            throw new ResourceAlreadyExistsException(String.format("dashboardUser with the email: %s already exists", dashboardUser.getEmail()));

        // the record with this 'id' shouldn't already exist...
        // For now any newly added user is granted DASHBOARD_USER privileges only
        if (!dashboardUserRepository.existsById(dashboardUser.getId())) {
            Set<Privilege> defaultPrivilege = this.privilegeRepository.findByName("DASHBOARD_USER").map(Collections::singleton).orElse(Collections.emptySet());
            if (defaultPrivilege != null && defaultPrivilege.stream().count() == 1) {
                dashboardUser.setPrivileges(defaultPrivilege);
            }
            return dashboardUserRepository.save(dashboardUser);
        }

        throw new ResourceAlreadyExistsException("Dashboard User with the id: " + dashboardUser.getId() + " already exists.");
    }

    @Override
    public Iterable<DashboardUser> getAllDashboardUsers() {
        return dashboardUserRepository.findAll();
    }
}
