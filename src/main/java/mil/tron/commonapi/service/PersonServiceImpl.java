package mil.tron.commonapi.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.UUID;

import org.springframework.stereotype.Service;

import mil.tron.commonapi.person.Person;

@Service
public class PersonServiceImpl implements PersonService {
	HashMap<UUID, Person> persons = new HashMap<>();

	@Override
	public Person createPerson(Person person) {
		persons.put(person.getId(), person);
		
		return person;
	}

	@Override
	public Person updatePerson(UUID id, Person person) {
		Person replaced = persons.replace(id, person);
		
		return replaced != null ? person : null;
	}

	@Override
	public void deletePerson(UUID id) {
		persons.remove(id);
	}

	@Override
	public Collection<Person> getPersons() {
		return persons.values();
	}

	@Override
	public Person getPerson(UUID id) {
		return persons.get(id);
	}

}
