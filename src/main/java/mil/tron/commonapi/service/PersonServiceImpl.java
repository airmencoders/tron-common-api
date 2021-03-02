package mil.tron.commonapi.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.entity.ranks.Rank;
import mil.tron.commonapi.repository.ranks.RankRepository;
import mil.tron.commonapi.service.utility.PersonUniqueChecksService;
import org.modelmapper.Conditions;
import org.springframework.stereotype.Service;

import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.repository.PersonRepository;

@Service
public class PersonServiceImpl implements PersonService {
	private PersonRepository repository;
	private PersonUniqueChecksService personChecksService;
	private RankRepository rankRepository;
	private final DtoMapper modelMapper;
	private final ObjectMapper objMapper;

	public PersonServiceImpl(PersonRepository repository, PersonUniqueChecksService personChecksService, RankRepository rankRepository) {
		this.repository = repository;
		this.personChecksService = personChecksService;
		this.rankRepository = rankRepository;
		this.modelMapper = new DtoMapper();
		modelMapper.getConfiguration().setPropertyCondition(Conditions.isNotNull());
		objMapper = new ObjectMapper();
	}

	@Override
	public PersonDto createPerson(PersonDto dto) {
		Person entity = convertToEntity(dto);
		if (repository.existsById(entity.getId()))
			throw new ResourceAlreadyExistsException("Person resource with the id: " + entity.getId() + " already exists.");

		if(!personChecksService.personEmailIsUnique(entity))
			throw new ResourceAlreadyExistsException(String.format("Person resource with the email: %s already exists", entity.getEmail()));
			
		return convertToDto(repository.save(entity));
	}

	@Override
	public PersonDto updatePerson(UUID id, PersonDto dto) {
		Person entity = convertToEntity(dto);
		// Ensure the id given matches the id of the object given
		if (!id.equals(entity.getId()))
			throw new InvalidRecordUpdateRequest(String.format("ID: %s does not match the resource ID: %s", id, entity.getId()));
		
		Optional<Person> dbPerson = repository.findById(id);
		
		if (dbPerson.isEmpty())
			throw new RecordNotFoundException("Person resource with the ID: " + id + " does not exist.");
		
		if (!personChecksService.personEmailIsUnique(entity)) {
			throw new InvalidRecordUpdateRequest(String.format("Email: %s is already in use.", entity.getEmail()));
		}
		
		return convertToDto(repository.save(entity));
	}

	@Override
	public PersonDto patchPerson(UUID id, JsonPatch patch) {

		Optional<Person> dbPerson = repository.findById(id);

		if (dbPerson.isEmpty()) {
			throw new RecordNotFoundException("Person resource with the ID: " + id + " does not exist.");
		}

		Person patchedPerson = applyPatchToPerson(patch, dbPerson.get());

		return convertToDto(repository.save(patchedPerson));
	}

	@Override
	public void deletePerson(UUID id) {
		if (repository.existsById(id)) {
			repository.deleteById(id);
		}
		else {
			throw new RecordNotFoundException("Record with ID: " + id.toString() + " not found.");
		}
	}

	@Override
	public Iterable<PersonDto> getPersons() {
		return StreamSupport
				.stream(repository.findAll().spliterator(), false)
				.map(this::convertToDto)
				.collect(Collectors.toList());
	}

	@Override
	public Person getPerson(UUID id) {
		return repository.findById(id).orElseThrow(() -> new RecordNotFoundException("Person resource with ID: " + id + " does not exist."));
	}

	@Override
	public PersonDto getPersonDto(UUID id) {
		return convertToDto(getPerson(id));
	}

	@Override
	public boolean exists(UUID id){
		return repository.existsById(id);
	}

	@Override
	public List<PersonDto> bulkAddPeople(List<PersonDto> dtos) {
		List<PersonDto> added = new ArrayList<>();
		for (PersonDto dto : dtos) {
			added.add(this.createPerson(dto));
		}
		return added;
	}

	@Override
	public PersonDto convertToDto(Person entity) {
		PersonDto dto = modelMapper.map(entity, PersonDto.class);
		Rank rank = entity.getRank();
		if (rank != null) {
			dto.setRank(rank.getAbbreviation());
			dto.setBranch(entity.getRank().getBranchType());
		}
		return dto;
	}

	@Override
	public Person convertToEntity(PersonDto dto) {
		Person entity = modelMapper.map(dto, Person.class);
		entity.setRank(rankRepository.findByAbbreviationAndBranchType(dto.getRank(), dto.getBranch()).orElseThrow(() -> new RecordNotFoundException(dto.getBranch() + " Rank '" + dto.getRank() + "' does not exist.")));
		return entity;
	}

	@Override
	public Person applyPatchToPerson(JsonPatch patch, Person person) {
		try {
			JsonNode patched = patch.apply(objMapper.convertValue(person, JsonNode.class));
			return objMapper.treeToValue(patched, Person.class);
		}
		catch (JsonPatchException | JsonProcessingException e) {
			throw new InvalidRecordUpdateRequest(String.format("Error patching person with email %s.", person.getEmail()));
		}
	}
}
