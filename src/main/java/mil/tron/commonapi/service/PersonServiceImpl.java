package mil.tron.commonapi.service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import mil.tron.commonapi.dto.*;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.dto.persons.*;
import mil.tron.commonapi.entity.PersonMetadata;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.ranks.Rank;
import mil.tron.commonapi.repository.PersonMetadataRepository;
import mil.tron.commonapi.repository.ranks.RankRepository;
import mil.tron.commonapi.service.utility.PersonUniqueChecksService;
import static mil.tron.commonapi.service.utility.ReflectionUtils.*;
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
	private PersonMetadataRepository personMetadataRepository;
	private final DtoMapper modelMapper;
	private static final Map<Branch, Set<String>> validProperties = Map.of(
			Branch.USAF, fields(Airman.class),
			Branch.USCG, fields(CoastGuardsman.class),
			Branch.USMC, fields(Marine.class),
			Branch.USN, fields(Sailor.class),
			Branch.USA, fields(Soldier.class),
			Branch.USSF, fields(Spaceman.class),
			Branch.OTHER, Collections.emptySet()
	);

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

		checkValidMetadataProperties(dto.getBranch(), dto.getMeta());
		Person resultEntity = repository.save(entity);
		PersonDto result = convertToDto(resultEntity);
		if (dto.getMeta() != null) {
			dto.getMeta().forEach((key, value) -> {
				resultEntity.getMetadata().add(new PersonMetadata(result.getId(), key, value));
				result.setMetaProperty(key, value);
			});
			personMetadataRepository.saveAll(resultEntity.getMetadata());
		}
		return result;
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

		return updateMetadata(dto.getBranch(), entity, dbPerson, dto.getMeta());
	}

	private PersonDto updateMetadata(Branch branch, Person updatedEntity, Optional<Person> dbEntity, Map<String, String> metadata) {
		checkValidMetadataProperties(branch, metadata);
		List<PersonMetadata> toDelete = new ArrayList<>();
		if(dbEntity.isPresent()) {
			dbEntity.get().getMetadata().forEach(m -> {
				updatedEntity.getMetadata().add(m);
				if (metadata == null || !metadata.containsKey(m.getKey())) {
					toDelete.add(m);
				}
			});
		}
		if (metadata != null) {
			metadata.forEach((key, value) -> {
				Optional<PersonMetadata> match = updatedEntity.getMetadata().stream().filter(x -> x.getKey() == key).findAny();
				if (match.isPresent()) {
					if (value == null) {
						toDelete.add(match.get());
					} else {
						match.get().setValue(value);
					}
				} else if (value != null) {
					updatedEntity.getMetadata().add(personMetadataRepository.save(new PersonMetadata(updatedEntity.getId(), key, value)));
				}
			});
		}
		// we have to save the person entity first, then try to delete metadata: hibernate seems to get confused
		// if we try to remove metadata rows from the person's metadata property and it generates invalid SQL
		PersonDto result = convertToDto(repository.save(updatedEntity));
		personMetadataRepository.deleteAll(toDelete);
		toDelete.forEach(m -> result.removeMetaProperty(m.getKey()));
		return result;
	}

	private void checkValidMetadataProperties(Branch branch, Map<String, String> metadata) {
		if (metadata != null) {
			if (branch == null) {
				branch = Branch.OTHER;
			}
			Set<String> properties = validProperties.get(branch);
			Set<String> unknownProperties = new HashSet<>();
			metadata.forEach((key, value) -> {
				if (!properties.contains(key)) {
					unknownProperties.add(key);
				}
			});
			if (unknownProperties.size() > 0) {
				throw new InvalidRecordUpdateRequest(String.format("Invalid properties for %s: %s", branch, String.join(", ", unknownProperties)));
			}
		}
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
	public PersonDto convertToDto(Person entity) {
		PersonDto dto = modelMapper.map(entity, PersonDto.class);
		Rank rank = entity.getRank();
		if (rank != null) {
			dto.setRank(rank.getAbbreviation());
			dto.setBranch(entity.getRank().getBranchType());
		}
		entity.getMetadata().stream().forEach(m -> dto.setMetaProperty(m.getKey(), m.getValue()));
		return dto;
	}

	@Override
	public Person convertToEntity(PersonDto dto) {
		Person entity = modelMapper.map(dto, Person.class);
		entity.setRank(rankRepository.findByAbbreviationAndBranchType(dto.getRank(), dto.getBranch()).orElseThrow(() -> new RecordNotFoundException(dto.getBranch() + " Rank '" + dto.getRank() + "' does not exist.")));
		return entity;
	}
}
