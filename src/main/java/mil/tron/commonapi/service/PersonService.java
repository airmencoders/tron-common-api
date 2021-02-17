package mil.tron.commonapi.service;

import java.util.List;
import java.util.UUID;

import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.entity.Person;

public interface PersonService {
	PersonDto createPerson(PersonDto dto);
	PersonDto updatePerson(UUID id, PersonDto dto);
	void deletePerson(UUID id);
	Iterable<PersonDto> getPersons(String metadataProperties);
	PersonDto getPersonDto(UUID id, String metadataProperties);
	Person getPerson(UUID id);
	boolean exists(UUID id);

	List<PersonDto> bulkAddPeople(List<PersonDto> dtos);

	PersonDto convertToDto(Person entity, String metadataProperties);
	Person convertToEntity(PersonDto dto);
}
