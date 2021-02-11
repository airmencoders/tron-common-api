package mil.tron.commonapi.service;

import java.util.List;
import java.util.UUID;

import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.Privilege;

public interface PersonService {
	PersonDto createPerson(PersonDto dto);
	PersonDto updatePerson(UUID id, PersonDto dto);
	void deletePerson(UUID id);
	Iterable<PersonDto> getPersons();
	PersonDto getPersonDto(UUID id);
	Person getPerson(UUID id);
	boolean exists(UUID id);

	List<PersonDto> bulkAddPeople(List<PersonDto> dtos);

	PersonDto convertToDto(Person entity);
	Person convertToEntity(PersonDto dto);

	void addPrivilege(UUID id, Privilege priv);
	void removePrivilege(UUID id, Privilege priv);
	void removeAllPrivileges(UUID id);
}
