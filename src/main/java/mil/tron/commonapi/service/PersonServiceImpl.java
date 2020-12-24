package mil.tron.commonapi.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.repository.PersonRepository;

@Service
public class PersonServiceImpl implements PersonService {
	private PersonRepository repository;
	
	public PersonServiceImpl(PersonRepository repository) {
		this.repository = repository;
	}

	@Override
	public Person createPerson(Person person) {
		if (repository.existsById(person.getId())) 
			throw new ResourceAlreadyExistsException("Person resource with the id: " + person.getId() + " already exists.");
		
		/**
		 * Unique Email Check
		 * 
		 * If the email is null, then skip the check.
		 * 
		 * Else, check if the email already exists in the database
		 */
		if(person.getEmail() != null && repository.findByEmailIgnoreCase(person.getEmail()).isPresent())
			throw new ResourceAlreadyExistsException(String.format("Person resource with the email: %s already exists", person.getEmail()));
			
		return repository.save(person);
	}

	@Override
	public Person updatePerson(UUID id, Person person) {
		// Ensure the id given matches the id of the object given
		if (!id.equals(person.getId()))
			throw new InvalidRecordUpdateRequest(String.format("ID: %s does not match the resource ID: %s", id, person.getId()));
		
		Optional<Person> dbPerson = repository.findById(id);
		
		if (dbPerson.isEmpty())
			throw new RecordNotFoundException("Person resource with the ID: " + id + " does not exist.");
		
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
		String dbPersonEmail = dbPerson.get().getEmail();
		String personEmail = person.getEmail();
		if (personEmail != null && !personEmail.equalsIgnoreCase(dbPersonEmail) && repository.findByEmailIgnoreCase(personEmail).isPresent()) 
			throw new InvalidRecordUpdateRequest(String.format("Email: %s is already in use.", person.getEmail()));
		
		return repository.save(person);
	}

	@Override
	public void deletePerson(UUID id) {
		if (repository.existsById(id)) {
			repository.deleteById(id);
		}
		else {
			throw new RecordNotFoundException("Record with ID: " + id.toString() + " not found.");
		}
	}

	@Override
	public Iterable<Person> getPersons() {
		return repository.findAll();
	}

	@Override
	public Person getPerson(UUID id) {
		return repository.findById(id).orElseThrow(() -> new RecordNotFoundException("Person resource with ID: " + id + " does not exist."));
	}

	@Override
	public List<Person> bulkAddPeople(List<Person> people) {
		List<Person> addedPeople = new ArrayList<>();
		for (Person p : people) {
			addedPeople.add(this.createPerson(p));
		}
		return addedPeople;
	}
}
