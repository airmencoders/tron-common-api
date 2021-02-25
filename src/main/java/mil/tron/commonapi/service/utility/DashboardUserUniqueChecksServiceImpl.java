package mil.tron.commonapi.service.utility;

import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.DashboardUserRepository;
import org.springframework.stereotype.Service;

@Service
public class DashboardUserUniqueChecksServiceImpl implements DashboardUserUniqueChecksService {

    private DashboardUserRepository userRepo;

    public DashboardUserUniqueChecksServiceImpl(DashboardUserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @Override
    public boolean UserEmailIsUnique(DashboardUser user) {
        if (user.getId() != null && userRepo.existsById(user.getId())) {
            DashboardUser dbUser = userRepo.findById(user.getId()).orElseThrow(() ->
                    new RecordNotFoundException("Error retrieving Record with UUID: " + user.getId())
            );

            /**
             * Unique Email Check
             *
             * Compare the given resource with the
             * same resource from the database.
             *
             * If the updated email is null or blank, skip the unique check
             * because null can exist and does not break the unique
             * email constraint.
             *
             * Check if the update contains a change in email.
             *
             * Check the database if any person exists with the
             * new email. If a person exists with the new email,
             * throw an exception to maintain unique email constraint.
             */
            String dbPersonEmail = dbUser.getEmail();
            String personEmail = user.getEmail();
            return (personEmail == null || personEmail.equalsIgnoreCase(dbPersonEmail) || userRepo.findByEmailIgnoreCase(personEmail).isEmpty());

        } else {
            return (user.getEmail() == null || userRepo.findByEmailIgnoreCase(user.getEmail()).isEmpty());
        }
    }
}
