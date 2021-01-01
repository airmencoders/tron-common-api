package mil.tron.commonapi.service.utility;

import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.PersonRepository;
import org.springframework.stereotype.Service;

@Service
public class PersonUniqueChecksServiceImpl implements PersonUniqueChecksService {

    private PersonRepository personRepo;

    public PersonUniqueChecksServiceImpl(PersonRepository personRepo) {
        this.personRepo = personRepo;
    }

    @Override
    public boolean personEmailIsUnique(Person person) {
        if (person.getId() != null && personRepo.existsById(person.getId())) {
            Person dbPerson = personRepo.findById(person.getId()).orElseThrow(() ->
                    new RecordNotFoundException("Error retrieving Record with UUID: " + person.getId())
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
            String dbPersonEmail = dbPerson.getEmail();
            String personEmail = person.getEmail();
            return (personEmail == null || personEmail.equalsIgnoreCase(dbPersonEmail) || personRepo.findByEmailIgnoreCase(personEmail).isEmpty());

        } else {
            return (person.getEmail() == null || personRepo.findByEmailIgnoreCase(person.getEmail()).isEmpty());
        }
    }
}
