package mil.tron.commonapi.service;

import java.util.UUID;

import mil.tron.commonapi.person.Person;

public interface PersonService {
	Person createPerson(Person person);
	Person updatePerson(UUID id, Person person);
	void deletePerson(UUID id);
	Iterable<Person> getPersons();
	Person getPerson(UUID id);
}
