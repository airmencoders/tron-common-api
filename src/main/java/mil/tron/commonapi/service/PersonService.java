package mil.tron.commonapi.service;

import java.util.List;
import java.util.UUID;

import com.github.fge.jsonpatch.JsonPatch;
import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.entity.Person;

public interface PersonService {
	PersonDto createPerson(PersonDto dto);
	PersonDto updatePerson(UUID id, PersonDto dto);
	PersonDto patchPerson(UUID id, JsonPatch patch);

	void deletePerson(UUID id);
	Iterable<PersonDto> getPersons();
	PersonDto getPersonDto(UUID id);
	Person getPerson(UUID id);
	boolean exists(UUID id);

	List<PersonDto> bulkAddPeople(List<PersonDto> dtos);

	PersonDto convertToDto(Person entity);
	Person convertToEntity(PersonDto dto);
	Person applyPatchToPerson(JsonPatch patch, Person person);
}
