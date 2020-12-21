package mil.tron.commonapi.service;

import java.util.ArrayList;
import java.util.List;
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
		else 
			return repository.save(person);
	}

	@Override
	public Person updatePerson(UUID id, Person person) {
		// Ensure the id given matches the id of the object given
		if (!id.equals(person.getId()))
			throw new InvalidRecordUpdateRequest(String.format("ID: %s does not match the resource ID: %s", id, person.getId()));
		
		// Check that the resource already exists
		if (!repository.existsById(id))
			throw new RecordNotFoundException("Person resource with the ID: " + id + " does not exist.");
		
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
