package mil.tron.commonapi.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import mil.tron.commonapi.service.utility.PersonUniqueChecksService;
import org.springframework.stereotype.Service;

import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.repository.PersonRepository;

@Service
public class PersonServiceImpl implements PersonService {
	public static PersonRepository repository;
	private PersonUniqueChecksService personChecksService;

	public PersonServiceImpl(PersonRepository repository, PersonUniqueChecksService personChecksService) {
		this.repository = repository;
		this.personChecksService = personChecksService;
	}

	@Override
	public Person createPerson(Person person) {
		if (repository.existsById(person.getId())) 
			throw new ResourceAlreadyExistsException("Person resource with the id: " + person.getId() + " already exists.");

		if(!personChecksService.personEmailIsUnique(person))
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
		
		if (!personChecksService.personEmailIsUnique(person)) {
			throw new InvalidRecordUpdateRequest(String.format("Email: %s is already in use.", person.getEmail()));
		}
		
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
