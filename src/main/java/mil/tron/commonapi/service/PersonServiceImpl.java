package mil.tron.commonapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import com.google.common.collect.Sets;
import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.dto.PlatformJwtDto;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.dto.persons.*;
import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.PersonMetadata;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.ranks.Rank;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.pubsub.EventManagerService;
import mil.tron.commonapi.pubsub.messages.PersonChangedMessage;
import mil.tron.commonapi.pubsub.messages.PersonDeleteMessage;
import mil.tron.commonapi.repository.PersonMetadataRepository;
import mil.tron.commonapi.repository.PersonRepository;
import mil.tron.commonapi.repository.filter.FilterCriteria;
import mil.tron.commonapi.repository.filter.SpecificationBuilder;
import mil.tron.commonapi.repository.ranks.RankRepository;
import mil.tron.commonapi.service.fieldauth.EntityFieldAuthService;
import mil.tron.commonapi.service.utility.PersonUniqueChecksService;
import mil.tron.commonapi.service.utility.ValidatorService;
import org.assertj.core.util.Lists;
import org.modelmapper.Conditions;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.MethodArgumentNotValidException;

import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;

import static mil.tron.commonapi.service.utility.ReflectionUtils.checkNonPatchableFieldsUntouched;
import static mil.tron.commonapi.service.utility.ReflectionUtils.fields;

@Service
public class PersonServiceImpl implements PersonService {
	private PersonRepository repository;
	private PersonUniqueChecksService personChecksService;
	private RankRepository rankRepository;
	private PersonMetadataRepository personMetadataRepository;
	private EventManagerService eventManagerService;
	private OrganizationService organizationService;
	private final DtoMapper modelMapper;
	private final ObjectMapper objMapper;
	private EntityFieldAuthService entityFieldAuthService;
	private ValidatorService validatorService;
	private static final Map<Branch, Set<String>> validProperties = Map.of(
			Branch.USAF, fields(Airman.class),
			Branch.USCG, fields(CoastGuardsman.class),
			Branch.USMC, fields(Marine.class),
			Branch.USN, fields(Sailor.class),
			Branch.USA, fields(Soldier.class),
			Branch.USSF, fields(Spaceman.class),
			Branch.OTHER, Collections.emptySet()
	);

	private static final String DODID_ALREADY_EXISTS_ERROR = "Person resource with the dodid: %s already exists";

	@SuppressWarnings("squid:S00107")
	public PersonServiceImpl(PersonRepository repository,
							 PersonUniqueChecksService personChecksService,
							 RankRepository rankRepository,
							 PersonMetadataRepository personMetadataRepository,
							 EventManagerService eventManagerService,
							 EntityFieldAuthService entityFieldAuthService,
							 @Lazy OrganizationService organizationService,
							 ValidatorService validatorService) {
		this.repository = repository;
		this.personChecksService = personChecksService;
		this.rankRepository = rankRepository;
		this.personMetadataRepository = personMetadataRepository;
		this.eventManagerService = eventManagerService;
		this.organizationService = organizationService;
		this.entityFieldAuthService = entityFieldAuthService;
		this.validatorService = validatorService;
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

		if (!personChecksService.personDodidIsUnique(entity))
			throw new ResourceAlreadyExistsException(String.format(DODID_ALREADY_EXISTS_ERROR, entity.getDodid()));

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

	/**
	 * Creates a new Person in the database from using a P1 JWT's information
	 * @param dto the P1 Jwt Dto
	 * @return the created person entity (if all went well)
	 */
	@Override
	public PersonDto createPersonFromJwt(PlatformJwtDto dto) {

		// map the PlatformJwtDto to a PersonDto
		PersonDto personDto = new PersonDto();
		personDto.setFirstName(dto.getGivenName());
		personDto.setLastName(dto.getFamilyName());
		personDto.setEmail(dto.getEmail());
		personDto.setDodid(dto.getDodId());

		// now convert branch
		if (dto.getAffiliation() != null) {
			switch (dto.getAffiliation()) {
				case "US Air Force":
				case "US Air Force Reserve":
				case "US Air National Guard":
					personDto.setBranch(Branch.USAF);
					break;
				case "US Army":
				case "US Army Reserve":
				case "US Army National Guard":
					personDto.setBranch(Branch.USA);
					break;
				case "US Marine Corps":
				case "US Marine Corps Reserve":
					personDto.setBranch(Branch.USMC);
					break;
				case "US Coast Guard":
				case "US Coast Guard Reserve":
					personDto.setBranch(Branch.USCG);
					break;
				case "US Navy":
				case "US Navy Reserve":
					personDto.setBranch(Branch.USN);
					break;
				case "US Space Force":
					personDto.setBranch(Branch.USSF);
					break;
				default:
					personDto.setBranch(Branch.OTHER);
					break;
			}
		}
		else {
			personDto.setBranch(Branch.OTHER);
		}

		// now convert rank, P1 uses Pay Grade for "rank"
		personDto.setRank(Lists.newArrayList(rankRepository.findAll())
				.stream()
				.filter(item -> item.getPayGrade().equals(dto.getRank()) && item.getBranchType().equals(personDto.getBranch()))
				.findFirst()
					.orElse(rankRepository.findByAbbreviationAndBranchType("Unk", Branch.OTHER)
					.orElseThrow(() -> new RecordNotFoundException("Unable to find rank match")))
				.getAbbreviation());

		return createPerson(personDto);
	}

	/**
	 * Updates a person by keying off their email (used for the self update endpoint) where we
	 * need to rely on the email from the JWT instead of a spoof-able UUID
	 * @param email email of person to update
	 * @param dto the data to commit to the db
	 * @return the updated person record
	 */
	@Override
	public PersonDto updatePersonByEmail(String email, PersonDto dto) {

		Person entity = repository.findByEmailIgnoreCase(email)
				.orElseThrow(() -> new RecordNotFoundException("Cannot find person with email " + email));

		return updatePerson(entity.getId(), dto);
	}

	@Override
	public PersonDto updatePerson(UUID id, PersonDto dto) {
		Person entity = convertToEntity(dto);
		
		// Set the correct id
		entity.setId(id);

		Optional<Person> dbPerson = repository.findById(id);

		if (dbPerson.isEmpty())
			throw this.buildRecordNotFoundForPerson(id);

		if (!personChecksService.personEmailIsUnique(entity)) {
			throw new InvalidRecordUpdateRequest(String.format("Email: %s is already in use.", entity.getEmail()));
		}

		if (!personChecksService.personDodidIsUnique(entity))
			throw new ResourceAlreadyExistsException(String.format(DODID_ALREADY_EXISTS_ERROR, entity.getDodid()));

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

		Person adjudicatedEntity = this.applyFieldAuthority(updatedEntity);

		// we have to save the person entity first, then try to delete metadata: hibernate seems to get confused
		// if we try to remove metadata rows from the person's metadata property and it generates invalid SQL
		PersonDto result = convertToDto(repository.save(adjudicatedEntity), null);
		personMetadataRepository.deleteAll(toDelete);
		toDelete.forEach(m -> result.removeMetaProperty(m.getKey()));
		return result;
	}

	// helper that applies entity field authorization for us
	private Person applyFieldAuthority(Person incomingEntity) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return entityFieldAuthService.adjudicatePersonFields(incomingEntity, authentication);
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
	public PersonDto patchPerson(UUID id, JsonPatch patch) throws MethodArgumentNotValidException {
		Optional<Person> dbPerson = repository.findById(id);

		if (dbPerson.isEmpty()) {
			throw this.buildRecordNotFoundForPerson(id);
		}
		// patch must be done using a DTO
		PersonDto dbPersonDto = convertToDto(dbPerson.get(), PersonConversionOptions
				.builder()
				.membershipsIncluded(true)
				.metadataIncluded(true)
				.leadershipsIncluded(true)
				.build());

		PersonDto patchedPersonDto = applyPatchToPerson(patch, dbPersonDto);

		// check we didnt change anything on any NonPatchableFields
		checkNonPatchableFieldsUntouched(dbPersonDto, patchedPersonDto);

		// Validate the patched person
		validatorService.isValid(patchedPersonDto, PersonDto.class);

		Person patchedPerson = convertToEntity(patchedPersonDto);

		if (!personChecksService.personEmailIsUnique(patchedPerson)) {
			throw new InvalidRecordUpdateRequest(String.format("Email: %s is already in use.", patchedPerson.getEmail()));
		}

		if (!personChecksService.personDodidIsUnique(patchedPerson))
			throw new ResourceAlreadyExistsException(String.format(DODID_ALREADY_EXISTS_ERROR, patchedPerson.getDodid()));


		PersonDto updatedPerson = updateMetadata(patchedPersonDto.getBranch(), patchedPerson, dbPerson, patchedPersonDto.getMeta());

		PersonChangedMessage message = new PersonChangedMessage();
		message.addPersonId(updatedPerson.getId());
		eventManagerService.recordEventAndPublish(message);

		return updatedPerson;
	}

	@Override
	public void deletePerson(UUID id) {
		if (repository.existsById(id)) {

			organizationService.removeLeaderByUuid(id);
			repository.deleteById(id);

			PersonDeleteMessage message = new PersonDeleteMessage();
			message.addPersonId(id);
			eventManagerService.recordEventAndPublish(message);

		} else {
			throw new RecordNotFoundException("Record with ID: " + id.toString() + " not found.");
		}
	}

	@Override
	public Iterable<PersonDto> getPersons(PersonConversionOptions options, Pageable page) {
		return repository.findBy(page).stream().map(person -> convertToDto(person, options)).collect(Collectors.toList());
	}
	
	@Override
	public Slice<PersonDto> getPersonsSlice(PersonConversionOptions options, Pageable page) {
		Slice<Person> slice = repository.findBy(page);
		
		return slice.map((Person entity) -> convertToDto(entity, options));
	}
	
	@Override
	public Page<PersonDto> getPersonsPage(PersonConversionOptions options, Pageable page) {
		Page<Person> pagedResponse = repository.findAll(page);
		
		return pagedResponse.map((Person entity) -> convertToDto(entity, options));
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
	public Person getPersonFilter(PersonFindType type, String value) {
		if (type == null || value == null) {
			throw new BadRequestException("Filter Type and Value cannot be null");
		}
		
		Optional<Person> person;
		switch(type) {
			case DODID:
				person = repository.findByDodidIgnoreCase(value);
				break;
			case EMAIL:
				person = repository.findByEmailIgnoreCase(value);
				break;
			default:
				throw new BadRequestException("Unknown filter type: " + type);
		}
		
		return person.orElseThrow(() -> new RecordNotFoundException(String.format("Person filtered by: %s with value %s not found.", type.toString(), value)));
	}

	@Override
	public boolean exists(UUID id) {
		return repository.existsById(id);
	}

	/**
	 * Bulk creates new persons.
	 * If any fail, then all of the prior successful inserts are rolled back, and non-successful status is returned
	 * Only fires one pub-sub event containing all new Person UUIDs
	 *
	 * @param dtos new Persons to create
	 * @return new Person dtos created
	 */
	@Transactional
	@Override
	public List<PersonDto> bulkAddPeople(List<PersonDto> dtos) {
		List<PersonDto> added = new ArrayList<>();  // list of UUIDs that get successfully added for pubsub broadcast

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
        if (options.isMetadataIncluded() && entity.getMetadata() != null) {
		    entity.getMetadata().stream().forEach(m -> dto.setMetaProperty(m.getKey(), m.getValue()));
        }
        if (options.isLeadershipsIncluded() && entity.getOrganizationLeaderships() != null) {
            dto.setOrganizationLeaderships(entity.getOrganizationLeaderships().stream().map(x -> x.getId()).collect(Collectors.toSet()));
        }
        if (options.isMembershipsIncluded() && entity.getOrganizationMemberships() != null) {
            dto.setOrganizationMemberships(entity.getOrganizationMemberships().stream().map(x -> x.getId()).collect(Collectors.toSet()));
        }
		return dto;
	}

	@Override
	public Person convertToEntity(PersonDto dto) {
		Person entity = modelMapper.map(dto, Person.class);
		entity.setRank(rankRepository.findByAbbreviationAndBranchType(dto.getRank(), dto.getBranch())
				.orElse(rankRepository.findByAbbreviationAndBranchType("Unk", Branch.OTHER)
						.orElseThrow(() -> new RecordNotFoundException("Unable to find rank match"))));
		return entity;
	}

	@Override
	public PersonDto applyPatchToPerson(JsonPatch patch, PersonDto personDto) {
		try {
			JsonNode patched = patch.apply(objMapper.convertValue(personDto, JsonNode.class));
			return objMapper.treeToValue(patched, PersonDto.class);
		}
		catch (NullPointerException e) {
			// apparently JSONPatch lib throws a NullPointerException on a null path, instead of a nicer one
			throw new InvalidRecordUpdateRequest("Json Patch cannot have a null path value");
		}
		catch (JsonPatchException | JsonProcessingException e) {
			throw new InvalidRecordUpdateRequest(String.format("Error patching person with email %s.", personDto.getEmail()));
		}
	}

	private RecordNotFoundException buildRecordNotFoundForPerson(UUID personId) {
		return new RecordNotFoundException("Person resource with ID: " + personId + " does not exist.");
	}

	@Override
	public Page<PersonDto> getPersonsPageSpec(PersonConversionOptions options, List<FilterCriteria> filterCriteria,
			Pageable page) {
		
		/**
		 * Transforms criteria for fields to account for join attributes.
		 * Takes the name of the field from the DTO and transforms
		 * the criteria to use the field name from the entity.
		 * 
		 * EX: rank field on PersonDto corresponds to the string Abbreviation field of Rank
		 */
		filterCriteria = filterCriteria.stream().map(criteria -> {
			switch (criteria.getField()) {
				case PersonDto.RANK_FIELD:
					criteria.transformToJoinAttribute(Rank.ABBREVIATION_FIELD, Person.RANK_FIELD);
					break;
					
				case PersonDto.ORG_MEMBERSHIPS_FIELD:
					criteria.transformToJoinAttribute(Organization.ID_FIELD, Person.ORG_MEMBERSHIPS_FIELD);
					break;
					
				case PersonDto.ORG_LEADERSHIPS_FIELD:
					criteria.transformToJoinAttribute(Organization.ID_FIELD, Person.ORG_LEADERSHIPS_FIELD);
					break;
					
				case PersonDto.BRANCH_FIELD:
					criteria.transformToJoinAttribute(Rank.BRANCH_TYPE_FIELD, Person.RANK_FIELD);
					break;
					
				default:
					break;
			}
				
			return criteria;
		}).collect(Collectors.toList());
		
		Specification<Person> spec = SpecificationBuilder.getSpecificationFromFilters(filterCriteria);
		Page<Person> pagedResponse = repository.findAll(spec, page);
		
		return pagedResponse.map((Person entity) -> convertToDto(entity, options));
	}
}
