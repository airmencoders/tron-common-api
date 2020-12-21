package mil.tron.commonapi.service;

import java.util.List;
import java.util.UUID;

import mil.tron.commonapi.entity.Person;

public interface PersonService {
	Person createPerson(Person person);
	Person updatePerson(UUID id, Person person);
	void deletePerson(UUID id);
	Iterable<Person> getPersons();
	Person getPerson(UUID id);

	List<Person> bulkAddPeople(List<Person> people);
}
