package mil.tron.commonapi.service;

import java.util.Collection;
import java.util.UUID;

import javax.persistence.EntityNotFoundException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import mil.tron.commonapi.person.Person;
import mil.tron.commonapi.repository.PersonRepository;

@Service
public class PersonServiceImpl implements PersonService {
	
	@Autowired
	private PersonRepository repository;
	
	public PersonServiceImpl() {

	}

	@Override
	public Person createPerson(Person person) {
		return repository.save(person);
	}

	@Override
	public Person updatePerson(UUID id, Person person) {
		repository.findById(id).ifPresentOrElse(
				(p1) -> { 
					repository.save(person);
				}, 
				() -> {
					throw new EntityNotFoundException(String.format("Person with id: %s does not exist", id.toString()));
				});
		
		return person;
	}

	@Override
	public void deletePerson(UUID id) {
		repository.deleteById(id);
		
	}

	@Override
	public Collection<Person> getPersons() {
		return (Collection<Person>) repository.findAll();
	}

	@Override
	public Person getPerson(UUID id) {
		Person person = repository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException(String.format("Could not find person by id: %s", id.toString())));
		
		return person;
	}

}
