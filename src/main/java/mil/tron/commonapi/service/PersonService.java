package mil.tron.commonapi.service;

import com.github.fge.jsonpatch.JsonPatch;
import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.dto.PlatformJwtDto;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.repository.filter.FilterCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.List;
import java.util.UUID;

public interface PersonService {
	PersonDto createPerson(PersonDto dto);
	PersonDto createPersonFromJwt(PlatformJwtDto dto);
	PersonDto updatePerson(UUID id, PersonDto dto);
	PersonDto patchPerson(UUID id, JsonPatch patch);

	void deletePerson(UUID id);
	Iterable<PersonDto> getPersons(PersonConversionOptions options, Pageable page);
	Page<PersonDto> getPersonsPage(PersonConversionOptions options, Pageable page);
	Slice<PersonDto> getPersonsSlice(PersonConversionOptions options, Pageable page);
	PersonDto getPersonDto(UUID id, PersonConversionOptions options);
	Person getPerson(UUID id);
	Person getPersonFilter(PersonFindType type, String value);
	boolean exists(UUID id);

	List<PersonDto> bulkAddPeople(List<PersonDto> dtos);

	PersonDto convertToDto(Person entity, PersonConversionOptions options);
	Person convertToEntity(PersonDto dto);
	PersonDto applyPatchToPerson(JsonPatch patch, PersonDto person);
	
	Page<PersonDto> getPersonsPageSpec(PersonConversionOptions options, List<FilterCriteria> filterCriteria, Pageable page);
}
