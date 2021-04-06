package mil.tron.commonapi.service;

import com.google.common.collect.Sets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.dto.persons.*;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.PersonMetadata;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.ranks.Rank;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.pubsub.EventManagerService;
import mil.tron.commonapi.pubsub.messages.PersonChangedMessage;
import mil.tron.commonapi.pubsub.messages.PersonDeleteMessage;
import mil.tron.commonapi.repository.PersonMetadataRepository;
import mil.tron.commonapi.repository.PersonRepository;
import mil.tron.commonapi.repository.ranks.RankRepository;
import mil.tron.commonapi.service.utility.PersonUniqueChecksService;
import org.modelmapper.Conditions;
import org.springframework.stereotype.Service;

import static mil.tron.commonapi.service.utility.ReflectionUtils.fields;

@Service
public class PersonServiceImpl implements PersonService {
	private PersonRepository repository;
	private PersonUniqueChecksService personChecksService;
	private RankRepository rankRepository;
	private PersonMetadataRepository personMetadataRepository;
	private EventManagerService eventManagerService;
	private final DtoMapper modelMapper;
	private final ObjectMapper objMapper;
	private static final Map<Branch, Set<String>> validProperties = Map.of(
			Branch.USAF, fields(Airman.class),
			Branch.USCG, fields(CoastGuardsman.class),
			Branch.USMC, fields(Marine.class),
			Branch.USN, fields(Sailor.class),
			Branch.USA, fields(Soldier.class),
			Branch.USSF, fields(Spaceman.class),
			Branch.OTHER, Collections.emptySet()
	);

	public PersonServiceImpl(PersonRepository repository,
							 PersonUniqueChecksService personChecksService,
							 RankRepository rankRepository,
							 PersonMetadataRepository personMetadataRepository,
							 EventManagerService eventManagerService) {
		this.repository = repository;
		this.personChecksService = personChecksService;
		this.rankRepository = rankRepository;
		this.personMetadataRepository = personMetadataRepository;
		this.eventManagerService = eventManagerService;
		this.modelMapper = new DtoMapper();
		modelMapper.getConfiguration().setPropertyCondition(Conditions.isNotNull());

        modelMapper.typeMap(Person.class, PersonDto.class)
            .addMappings(m -> m.skip(PersonDto::setPrimaryOrganizationId))
            .addMappings(m -> m.skip(PersonDto::setOrganizationLeaderships))
            .addMappings(m -> m.skip(PersonDto::setOrganizationMemberships));
		objMapper = new ObjectMapper();
	}

	/**
	 * Private helper to actually do the persisting of the Person entity to database.  This is
	 * broken out for pub-sub purposes.  This helper does NOT invoke a pub-sub message
	 * @param dto PersonDto entity to persist
	 */
	private PersonDto persistPerson(PersonDto dto) {
		Person entity = convertToEntity(dto);
		if (repository.existsById(entity.getId()))
			throw new ResourceAlreadyExistsException("Person resource with the id: " + entity.getId() + " already exists.");

		if (!personChecksService.personEmailIsUnique(entity))
			throw new ResourceAlreadyExistsException(String.format("Person resource with the email: %s already exists", entity.getEmail()));

		checkValidMetadataProperties(dto.getBranch(), dto.getMeta());
		Person resultEntity = repository.save(entity);
		PersonDto result = convertToDto(resultEntity, null);
		if (dto.getMeta() != null) {
			dto.getMeta().forEach((key, value) -> {
				resultEntity.getMetadata().add(new PersonMetadata(result.getId(), key, value));
				result.setMetaProperty(key, value);
			});
			personMetadataRepository.saveAll(resultEntity.getMetadata());
		}

		return result;
	}

	/**
	 * Creates a new person entity in the database.  Called from the controller for a POST to /person.
	 * This method uses internal helper persistPerson() to actually persist the entity to the database.
	 * This method is a wrapper around persistPerson() that fires a pub-sub message.
	 * @param dto The Person dto to persist
	 * @return the persisted Person Dto
	 */
	@Override
	public PersonDto createPerson(PersonDto dto) {
		PersonDto result = this.persistPerson(dto);
		PersonChangedMessage message = new PersonChangedMessage();
		message.addPersonId(result.getId());
		eventManagerService.recordEventAndPublish(message);

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
			throw this.buildRecordNotFoundForPerson(id);

		if (!personChecksService.personEmailIsUnique(entity)) {
			throw new InvalidRecordUpdateRequest(String.format("Email: %s is already in use.", entity.getEmail()));
		}

		PersonDto updatedPerson = updateMetadata(dto.getBranch(), entity, dbPerson, dto.getMeta());

		PersonChangedMessage message = new PersonChangedMessage();
		message.addPersonId(updatedPerson.getId());
		eventManagerService.recordEventAndPublish(message);

		return updatedPerson;
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
		PersonDto result = convertToDto(repository.save(updatedEntity), null);
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
			if (!unknownProperties.isEmpty()) {
				throw new InvalidRecordUpdateRequest(String.format("Invalid properties for %s: %s", branch, String.join(", ", unknownProperties)));
			}
		}
	}

	@Override
	public PersonDto patchPerson(UUID id, JsonPatch patch) {
		Optional<Person> dbPerson = repository.findById(id);

		if (dbPerson.isEmpty()) {
			throw this.buildRecordNotFoundForPerson(id);
		}
		// patch must be done using a DTO
		PersonDto dbPersonDto = convertToDto(dbPerson.get(),null);
		PersonDto patchedPersonDto = applyPatchToPerson(patch, dbPersonDto);
		Person patchedPerson = convertToEntity(patchedPersonDto);

		if (!personChecksService.personEmailIsUnique(patchedPerson)) {
			throw new InvalidRecordUpdateRequest(String.format("Email: %s is already in use.", patchedPerson.getEmail()));
		}

		PersonDto updatedPerson = updateMetadata(patchedPersonDto.getBranch(), patchedPerson, dbPerson, patchedPersonDto.getMeta());

		PersonChangedMessage message = new PersonChangedMessage();
		message.addPersonId(updatedPerson.getId());
		eventManagerService.recordEventAndPublish(message);

		return updatedPerson;
	}

	@Override
	public void deletePerson(UUID id) {
		if (repository.existsById(id)) {
			repository.deleteById(id);

			PersonDeleteMessage message = new PersonDeleteMessage();
			message.addPersonId(id);
			eventManagerService.recordEventAndPublish(message);

		} else {
			throw new RecordNotFoundException("Record with ID: " + id.toString() + " not found.");
		}
	}

	@Override
	public Iterable<PersonDto> getPersons(PersonConversionOptions options) {
		return StreamSupport
				.stream(repository.findAll().spliterator(), false)
				.map(p -> convertToDto(p, options))
				.collect(Collectors.toList());
	}

	@Override
	public Person getPerson(UUID id) {
		return repository.findById(id).orElseThrow(() -> this.buildRecordNotFoundForPerson(id));
	}

	@Override
	public PersonDto getPersonDto(UUID id, PersonConversionOptions options) {
		return convertToDto(getPerson(id), options);
	}

	@Override
	public boolean exists(UUID id) {
		return repository.existsById(id);
	}

	/**
	 * Bulk creates new persons.  Only fires one pub-sub event containing all new Person UUIDs
	 *
	 * @param dtos new Persons to create
	 * @return new Person dtos created
	 */
	@Override
	public List<PersonDto> bulkAddPeople(List<PersonDto> dtos) {
		List<PersonDto> added = new ArrayList<>();
		for (PersonDto dto : dtos) {
			added.add(this.persistPerson(dto));
		}

		// only send one pub-sub message for all added persons (new org Ids will be an array in the message body)
		List<UUID> addedIds = added.stream().map(PersonDto::getId).collect(Collectors.toList());

		PersonChangedMessage message = new PersonChangedMessage();
		message.setPersonIds(Sets.newHashSet(addedIds));
		eventManagerService.recordEventAndPublish(message);

		return added;
	}

	@Override
	public PersonDto convertToDto(Person entity, PersonConversionOptions options) {
        if (options == null) {
            options = new PersonConversionOptions();
        }
		PersonDto dto = modelMapper.map(entity, PersonDto.class);
		Rank rank = entity.getRank();
        if (rank != null) {
            dto.setRank(rank.getAbbreviation());
            dto.setBranch(entity.getRank().getBranchType());
        }
        if (entity.getPrimaryOrganization() != null) {
            dto.setPrimaryOrganizationId(entity.getPrimaryOrganization().getId());
        }
        if (options.isMetadataIncluded()) {
		    entity.getMetadata().stream().forEach(m -> dto.setMetaProperty(m.getKey(), m.getValue()));
        }
        if (options.isLeadershipsIncluded()) {
            dto.setOrganizationLeaderships(entity.getOrganizationLeaderships().stream().map(x -> x.getId()).collect(Collectors.toSet()));
        }
        if (options.isMembershipsIncluded()) {
            dto.setOrganizationMemberships(entity.getOrganizationMemberships().stream().map(x -> x.getId()).collect(Collectors.toSet()));
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
	public PersonDto applyPatchToPerson(JsonPatch patch, PersonDto personDto) {
		try {
			JsonNode patched = patch.apply(objMapper.convertValue(personDto, JsonNode.class));
			return objMapper.treeToValue(patched, PersonDto.class);
		}
		catch (JsonPatchException | JsonProcessingException e) {
			throw new InvalidRecordUpdateRequest(String.format("Error patching person with email %s.", personDto.getEmail()));
		}
	}

	private RecordNotFoundException buildRecordNotFoundForPerson(UUID personId) {
		return new RecordNotFoundException("Person resource with ID: " + personId + " does not exist.");
	}
}
