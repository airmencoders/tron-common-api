package mil.tron.commonapi.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import mil.tron.commonapi.person.Person;
import mil.tron.commonapi.repository.PersonRepository;

@Service
public class PersonServiceImpl implements PersonService {
	private PersonRepository repository;
	
	public PersonServiceImpl(PersonRepository repository) {
		this.repository = repository;
	}

	@Override
	public Person createPerson(Person person) {
		return repository.save(person);
	}

	@Override
	public Person updatePerson(UUID id, Person person) {
		// Ensure the id given matches the id of the object given
		if (!id.equals(person.getId()) || !repository.existsById(id))
			return null;
		
		return repository.save(person);
	}

	@Override
	public void deletePerson(UUID id) {
		repository.deleteById(id);
	}

	@Override
	public Iterable<Person> getPersons() {
		return repository.findAll();
	}

	@Override
	public Person getPerson(UUID id) {
		return repository.findById(id).orElse(null);
	}

}
