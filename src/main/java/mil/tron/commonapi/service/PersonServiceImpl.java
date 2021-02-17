package mil.tron.commonapi.service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.entity.PersonMetadata;
import mil.tron.commonapi.entity.ranks.Rank;
import mil.tron.commonapi.repository.PersonMetadataRepository;
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
	private static final String ALL = "$all";
	private PersonRepository repository;
	private PersonUniqueChecksService personChecksService;
	private RankRepository rankRepository;
	private PersonMetadataRepository personMetadataRepository;
	private final DtoMapper modelMapper;

	public PersonServiceImpl(PersonRepository repository, PersonUniqueChecksService personChecksService, RankRepository rankRepository, PersonMetadataRepository personMetadataRepository) {
		this.repository = repository;
		this.personChecksService = personChecksService;
		this.rankRepository = rankRepository;
		this.personMetadataRepository = personMetadataRepository;
		this.modelMapper = new DtoMapper();
		modelMapper.getConfiguration().setPropertyCondition(Conditions.isNotNull());
	}

	@Override
	public PersonDto createPerson(PersonDto dto) {
		Person entity = convertToEntity(dto);
		if (repository.existsById(entity.getId()))
			throw new ResourceAlreadyExistsException("Person resource with the id: " + entity.getId() + " already exists.");

		if (!personChecksService.personEmailIsUnique(entity))
			throw new ResourceAlreadyExistsException(String.format("Person resource with the email: %s already exists", entity.getEmail()));

		if (dto.getMeta() != null) {
			dto.getMeta().forEach((key, value) -> {
				entity.getMetadata().add(new PersonMetadata(entity.getId(), key, value));
			});
		}
		return convertToDto(repository.save(entity), ALL);
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

		dbPerson.get().getMetadata().forEach(entity.getMetadata()::add);
		if (dto.getMeta() != null) {
			dto.getMeta().forEach((key, value) -> {
				Optional<PersonMetadata> match = entity.getMetadata().stream().filter(x -> x.getKey() == key).findAny();
				if (match.isPresent()) {
					if (value == null) {
						personMetadataRepository.delete(match.get());
					} else {
						match.get().setValue(value);
					}
				} else if (value != null) {
					entity.getMetadata().add(personMetadataRepository.save(new PersonMetadata(id, key, value)));
				}
			});
		}
		return convertToDto(repository.save(entity), ALL);
	}

	@Override
	public void deletePerson(UUID id) {
		if (repository.existsById(id)) {
			repository.deleteById(id);
		} else {
			throw new RecordNotFoundException("Record with ID: " + id.toString() + " not found.");
		}
	}

	@Override
	public Iterable<PersonDto> getPersons(String metadataProperties) {
		return StreamSupport
				.stream(repository.findAll().spliterator(), false)
				.map(person -> convertToDto(person, metadataProperties))
				.collect(Collectors.toList());
	}

	@Override
	public Person getPerson(UUID id) {
		return repository.findById(id).orElseThrow(() -> new RecordNotFoundException("Person resource with ID: " + id + " does not exist."));
	}

	@Override
	public PersonDto getPersonDto(UUID id, String metadataProperties) {
		return convertToDto(getPerson(id), metadataProperties);
	}

	@Override
	public boolean exists(UUID id) {
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
	public PersonDto convertToDto(Person entity, String properties) {
		PersonDto dto = modelMapper.map(entity, PersonDto.class);
		Rank rank = entity.getRank();
		if (rank != null) {
			dto.setRank(rank.getAbbreviation());
			dto.setBranch(entity.getRank().getBranchType());
		}
		if (properties != null) {
			Stream<PersonMetadata> metadata = entity.getMetadata().stream();
			if (!ALL.equals(properties)) {
				Set<String> split = new HashSet<>(Arrays.stream(properties.split(",")).collect(Collectors.toSet()));
				metadata = metadata.filter(m -> split.contains(m.getKey()));
			}
			metadata.forEach(m -> dto.setMetaProperty(m.getKey(), m.getValue()));
		}
		return dto;
	}

	@Override
	public Person convertToEntity(PersonDto dto) {
		Person entity = modelMapper.map(dto, Person.class);
		entity.setRank(rankRepository.findByAbbreviationAndBranchType(dto.getRank(), dto.getBranch()).orElseThrow(() -> new RecordNotFoundException(dto.getBranch() + " Rank '" + dto.getRank() + "' does not exist.")));
		return entity;
	}
}
