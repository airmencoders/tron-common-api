package mil.tron.commonapi.service;

import java.util.UUID;

import mil.tron.commonapi.person.Person;

public interface PersonService {
	public abstract Person createPerson(Person person);
	public abstract Person updatePerson(UUID id, Person person);
	public abstract void deletePerson(UUID id);
	public abstract Iterable<Person> getPersons();
	public abstract Person getPerson(UUID id);
}
