package mil.tron.commonapi.service;

import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.DashboardUserRepository;
//import mil.tron.commonapi.service.utility.PersonUniqueChecksService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DashboardUserServiceImpl implements DashboardUserService {

    private DashboardUserRepository dashboardUserRepository;
//    private PersonUniqueChecksService personChecksService;

    public DashboardUserServiceImpl(DashboardUserRepository dashboardUserRepository) {
        this.dashboardUserRepository = dashboardUserRepository;
    }

    @Override
    public DashboardUser createDashboardUser(DashboardUser dashboardUser) {
        if (dashboardUser.getId() == null) {
            // we have to generate an ID manually since we're not using normal
            //  serial ID but rather an UUID for Person entity...
            dashboardUser.setId(UUID.randomUUID());
        }

//        if (!personChecksService.personEmailIsUnique(dashboardUser))
//            throw new ResourceAlreadyExistsException(String.format("dashboardUser with the email: %s already exists", dashboardUser.getEmail()));

        // the record with this 'id' shouldn't already exist...
        if (!dashboardUserRepository.existsById(dashboardUser.getId())) {
            return dashboardUserRepository.save(dashboardUser);
        }

        throw new ResourceAlreadyExistsException("Dashboard User with the id: " + dashboardUser.getId() + " already exists.");
//        return new DashboardUser();
    }

    @Override
    public Iterable<DashboardUser> getAllDashboardUsers() {
        return dashboardUserRepository.findAll();
    }
}
