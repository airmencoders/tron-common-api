package mil.tron.commonapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import com.google.common.collect.Sets;
import mil.tron.commonapi.controller.OrganizationController;
import mil.tron.commonapi.dto.OrganizationDto;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.dto.mixins.CustomOrganizationDtoMixin;
import mil.tron.commonapi.dto.mixins.CustomPersonDtoMixin;
import mil.tron.commonapi.dto.organizations.*;
import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.entity.OrganizationMetadata;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.orgtypes.Unit;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.pubsub.EventManagerService;
import mil.tron.commonapi.pubsub.messages.*;
import mil.tron.commonapi.repository.OrganizationMetadataRepository;
import mil.tron.commonapi.repository.OrganizationRepository;
import mil.tron.commonapi.repository.PersonRepository;
import mil.tron.commonapi.repository.filter.FilterCriteria;
import mil.tron.commonapi.repository.filter.SpecificationBuilder;
import mil.tron.commonapi.service.utility.OrganizationUniqueChecksService;
import org.modelmapper.AbstractConverter;
import org.modelmapper.Conditions;
import org.modelmapper.Converter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static mil.tron.commonapi.service.utility.ReflectionUtils.fields;

@Service
public class OrganizationServiceImpl implements OrganizationService {
	private final OrganizationRepository repository;
	private final PersonService personService;
	private final PersonRepository personRepository;
	private final OrganizationUniqueChecksService orgChecksService;
	private final OrganizationMetadataRepository organizationMetadataRepository;
	private final EventManagerService eventManagerService;
	private final DtoMapper modelMapper;
	private static final String RESOURCE_NOT_FOUND_MSG = "Resource with the ID: %s does not exist.";
	private static final String ORG_IS_IN_ANCESTRY_MSG = "Organization %s is already an ancestor to this organization.";
	
	private static final Map<Unit, Set<String>> validProperties = Map.of(
			Unit.FLIGHT, fields(Flight.class),
			Unit.GROUP, fields(Group.class),
			Unit.OTHER_USAF, fields(OtherUsaf.class),
			Unit.SQUADRON, fields(Squadron.class),
			Unit.WING, fields(Wing.class),
			Unit.ORGANIZATION, Collections.emptySet()
	);

	private final ObjectMapper objMapper;

	public OrganizationServiceImpl(
			OrganizationRepository repository,
			PersonRepository personRepository,
			PersonService personService,
			OrganizationUniqueChecksService orgChecksService,
			OrganizationMetadataRepository organizationMetadataRepository,
			EventManagerService eventManagerService) {

		this.repository = repository;
		this.personRepository = personRepository;
		this.personService = personService;
		this.orgChecksService = orgChecksService;
		this.organizationMetadataRepository = organizationMetadataRepository;
		this.eventManagerService = eventManagerService;
		this.modelMapper = new DtoMapper();
		this.objMapper = new ObjectMapper();
	}

	/**
	 * Finds a record by UUID that returns the raw entity type (not DTO)
	 *
	 * @param id UUID of the organization to find
	 * @return Organization entity object (if found), otherwise throws Exception
	 */
	@Override
	public Organization findOrganization(UUID id) {
		return repository.findById(id).orElseThrow(() -> new RecordNotFoundException(String.format(RESOURCE_NOT_FOUND_MSG, id)));
	}

	/**
	 * Adds a list of organizations as subordinate orgs to provided organization
	 *
	 * @param organizationId organization ID to modify
	 * @param orgIds         list of orgs by their UUIDs to add as subordinate organizations
	 * @return the persisted, modified organization
	 */
	@Override
	public Organization addOrg(UUID organizationId, List<UUID> orgIds) {
		Organization organization = repository.findById(organizationId)
				.orElseThrow(() -> new RecordNotFoundException(
						String.format(RESOURCE_NOT_FOUND_MSG, organizationId.toString())));

		for (UUID id : orgIds) {
			Organization subordinate = repository.findById(id).orElseThrow(
					() -> new InvalidRecordUpdateRequest(String.format(RESOURCE_NOT_FOUND_MSG, id)));

			if (!orgIsInAncestryChain(subordinate.getId(), organization)) {
				organization.addSubordinateOrganization(subordinate);
			} else {
				throw new InvalidRecordUpdateRequest(String.format(ORG_IS_IN_ANCESTRY_MSG, subordinate.getId()));
			}
		}

		Organization result = repository.save(organization);

		SubOrgAddMessage message = new SubOrgAddMessage();
		message.setParentOrgId(organizationId);
		message.setSubOrgsAdded(Sets.newHashSet(orgIds));
		eventManagerService.recordEventAndPublish(message);

		return result;
	}

	/**
	 * Removes a list of organizations as subordinate orgs from provided organization
	 *
	 * @param organizationId organization ID to modify
	 * @param orgIds         list of orgs by their UUIDs to remove from subordinate organizations
	 * @return the persisted, modified organization
	 */
	@Override
	public Organization removeOrg(UUID organizationId, List<UUID> orgIds) {
		Organization organization = repository.findById(organizationId)
				.orElseThrow(() -> new RecordNotFoundException(
						String.format(RESOURCE_NOT_FOUND_MSG, organizationId.toString())));

		for (UUID id : orgIds) {
			Organization subordinate = repository.findById(id).orElseThrow(
					() -> new InvalidRecordUpdateRequest(String.format(RESOURCE_NOT_FOUND_MSG, id)));

			organization.removeSubordinateOrganization(subordinate);
		}

		Organization result = repository.save(organization);

		SubOrgRemoveMessage message = new SubOrgRemoveMessage();
		message.setParentOrgId(organizationId);
		message.setSubOrgsRemoved(Sets.newHashSet(orgIds));
		eventManagerService.recordEventAndPublish(message);

		return result;
	}

	/**
	 * Removes members from an organization and re-persists it to db.
	 *
	 * @param organizationId UUID of the organization
	 * @param personIds      List of Person UUIDs to remove
	 * @return Organization entity object
	 */
	@Override
	public Organization removeMember(UUID organizationId, List<UUID> personIds) {
		Organization organization = repository.findById(organizationId).orElseThrow(
				() -> new RecordNotFoundException(String.format(RESOURCE_NOT_FOUND_MSG, organizationId.toString())));

        List<Person> updatedPersons = new ArrayList<>();
		for (UUID id : personIds) {
			Person person = personRepository.findById(id).orElseThrow(
					() -> new InvalidRecordUpdateRequest(String.format(RESOURCE_NOT_FOUND_MSG, id)));

            organization.removeMember(person);
            if (person.getPrimaryOrganization() != null && person.getPrimaryOrganization().getId() == organizationId) {
                person.setPrimaryOrganization(null);
                updatedPersons.add(person);
            }
		}

        Organization result = repository.save(organization);

        if (updatedPersons.size() > 0) {
            personRepository.saveAll(updatedPersons);
        }

		PersonOrgRemoveMessage message = new PersonOrgRemoveMessage();
		message.setParentOrgId(organizationId);
		message.setMembersRemoved(Sets.newHashSet(personIds));
		eventManagerService.recordEventAndPublish(message);

		return result;
	}

	/**
     * Adds members from an organization and re-persists it to db.
     *
     * @param organizationId UUID of the organization
     * @param personIds      List of Person UUIDs to remove
     * @param primary        Whether to set the org as the persons' primary org
     * @return Organization entity object
     */
	@Override
	public Organization addMember(UUID organizationId, List<UUID> personIds, boolean primary) {
		Organization organization = repository.findById(organizationId)
				.orElseThrow(() -> new RecordNotFoundException(
                        String.format(RESOURCE_NOT_FOUND_MSG, organizationId.toString())));
                        
        List<Person> updatedPersons = personIds.stream().map(id -> {
			Person person = personRepository.findById(id).orElseThrow(
                    () -> new InvalidRecordUpdateRequest("Provided person UUID " + id.toString() + " does not exist"));

            if (primary) {
                person.setPrimaryOrganization(organization);
            }
			organization.addMember(person);
            return person;
        }).collect(Collectors.toList());

        Organization result = repository.save(organization);
        
        if (primary) {
            personRepository.saveAll(updatedPersons);
        }

		PersonOrgAddMessage message = new PersonOrgAddMessage();
		message.setParentOrgId(organizationId);
		message.setMembersAdded(Sets.newHashSet(personIds));
		eventManagerService.recordEventAndPublish(message);

		return result;
	}

	/**
	 * Modifies non collection type attributes of the organization
	 *
	 * @param organizationId The UUID of the organization to modify
	 * @param attribs        A map of string key-values where keys are named of fields and values are the value to set to
	 * @return The modified Organization entity object
	 */
	@Override
	public OrganizationDto modify(UUID organizationId, Map<String, String> attribs) {  //NOSONAR
		Organization organization = repository.findById(organizationId).orElseThrow(
				() -> new RecordNotFoundException(String.format(RESOURCE_NOT_FOUND_MSG, organizationId.toString())));
		Map<String, String> metadata = new HashMap<>();
		attribs.forEach((k, v) -> {
			Field field = ReflectionUtils.findField(Organization.class, k);
			try {
				if (field != null) {
					String setterName = "set" + field.getName().substring(0, 1).toUpperCase() + field.getName().substring(1);
					Method setterMethod = organization.getClass().getMethod(setterName, field.getType());
					if (k.equals("id")) {
						throw new InvalidRecordUpdateRequest("Cannot set/modify this record ID field");
					} else if (k.equals("parentOrganization")) {
						setOrgParentConditionally(organization, v);
					} else if (v == null) {
						ReflectionUtils.invokeMethod(setterMethod, organization, (Object) null);
					} else if (field.getType().equals(Person.class)) {
						Person person = personRepository.findById(UUID.fromString(v))
								.orElseThrow(() -> new InvalidRecordUpdateRequest("Provided person UUID " + v + " does not match any existing records"));
						ReflectionUtils.invokeMethod(setterMethod, organization, person);
					} else if (field.getType().equals(Organization.class)) {
						Organization org = repository.findById(UUID.fromString(v)).orElseThrow(
								() -> new InvalidRecordUpdateRequest("Provided org UUID " + v + " does not match any existing records"));
						ReflectionUtils.invokeMethod(setterMethod, organization, org);
					} else if (field.getType().equals(String.class)) {
						ReflectionUtils.invokeMethod(setterMethod, organization, v);
					} else if (field.getType().equals(Branch.class)) {
						ReflectionUtils.invokeMethod(setterMethod, organization, Branch.valueOf(v));
					} else if (field.getType().equals(Unit.class)) {
						ReflectionUtils.invokeMethod(setterMethod, organization, Unit.valueOf(v));
					} else {
						throw new InvalidRecordUpdateRequest("Field: " + field.getName() + " is not of recognized type");
					}
				} else {
					metadata.put(k, v);
				}
			} catch (NoSuchMethodException e) {
				throw new InvalidRecordUpdateRequest("Provided field: " + field.getName() + " is not settable");
			}
		});

		return updateMetadata(organization, Optional.empty(), metadata);
	}

	@Override
	public OrganizationDto patchOrganization(UUID id, JsonPatch patch) {
		Optional<Organization> dbOrganization = this.repository.findById(id);

		if (dbOrganization.isEmpty()) {
			throw new RecordNotFoundException(String.format("Organization %s not found.", id));
		}

		OrganizationDto dbOrgDto = convertToDto(dbOrganization.get());
		OrganizationDto patchedOrgDto = applyPatchToOrganization(patch, dbOrgDto);
		Organization patchedOrg = convertToEntity(patchedOrgDto);

		// If patch changes name and the new name is not unique throw error
		if (!dbOrganization.get().getName().equalsIgnoreCase(patchedOrg.getName()) &&
			!this.orgChecksService.orgNameIsUnique(patchedOrg)) {
			throw new InvalidRecordUpdateRequest(String.format("Organization already exists with name %s",
					patchedOrg.getName()));
		}

		OrganizationDto updateOrganization = updateMetadata(patchedOrg, dbOrganization, patchedOrgDto.getMeta());

		OrganizationChangedMessage message = new OrganizationChangedMessage();
		message.addOrgId(id);
		eventManagerService.recordEventAndPublish(message);

		return updateOrganization;
	}

	/**
	 * Filters out organizations by type and branch.
	 *
	 * @param searchQuery name of org to match on for filtering (case in-sensitve)
	 * @param type        The unit type
	 * @param branch      The branch/service type (if null then ignores it)
	 * @return filtered list of Organizations
	 */
	@Override
	public Iterable<Organization> findOrganizationsByTypeAndService(String searchQuery, Unit type, Branch branch, Pageable page) {
		return repository.findBy(page).stream()
				.filter(item -> item.getName().toLowerCase().contains(searchQuery.toLowerCase()))
				.filter(item -> {
					if (type == null && branch == null) return true;
					else if (branch == null) return item.getOrgType().equals(type);
					else if (type == null) return item.getBranchType().equals(branch);
					else return item.getOrgType().equals(type) && item.getBranchType().equals(branch);
				})
				.collect(Collectors.toList());
	}

	/**
	 * Controller-facing method to filter out organizations by type and service
	 *
	 * @param searchQuery name of org to match on for filtering (case in-sensitve)
	 * @param type        The unit type
	 * @param branch      The branch service type (null to ignore)
	 * @return The filtered list of organizations
	 */
	@Override
	public Iterable<OrganizationDto> getOrganizationsByTypeAndService(String searchQuery, Unit type, Branch branch, Pageable page) {
		return StreamSupport
				.stream(this.findOrganizationsByTypeAndService(searchQuery, type, branch, page).spliterator(), false)
				.map(this::convertToDto)
				.collect(Collectors.toList());
	}

	/**
	 * Private helper to actually do the persisting of the Organization entity to database.  This is
	 * broken out for pub-sub purposes.  This helper does NOT invoke a pub-sub message
	 * @param organization OrganizationDto entity to persist
	 */
	private OrganizationDto persistOrganization(OrganizationDto organization) {
		Organization org = this.convertToEntity(organization);

		if (repository.existsById(org.getId()))
			throw new ResourceAlreadyExistsException(String.format("Resource with the ID: %s already exists.", org.getId()));

		if (!orgChecksService.orgNameIsUnique(org))
			throw new ResourceAlreadyExistsException(String.format("Resource with the Name: %s already exists.", org.getName()));

		// vet all this org's desired subordinate organizations, make sure none of them are already in this org's ancestry chain
		if (org.getSubordinateOrganizations() != null && !org.getSubordinateOrganizations().isEmpty()) {
			for (Organization subOrg : org.getSubordinateOrganizations()) {
				if (orgIsInAncestryChain(subOrg.getId(), org)) {
					throw new InvalidRecordUpdateRequest(String.format(ORG_IS_IN_ANCESTRY_MSG, subOrg.getId()));
				}
			}
		}

		checkValidMetadataProperties(organization.getOrgType(), organization.getMeta());
		Organization resultEntity = repository.save(org);
		OrganizationDto result = convertToDto(resultEntity);
		if (organization.getMeta() != null) {
			organization.getMeta().forEach((key, value) -> {
				resultEntity.getMetadata().add(new OrganizationMetadata(result.getId(), key, value));
				result.setMetaProperty(key, value);
			});
			organizationMetadataRepository.saveAll(resultEntity.getMetadata());
		}

		return result;
	}

	/**
	 * Creates a new organization and returns the DTO representation of which
	 *
	 * @param organization The DTO containing the new Org information with an optional UUID (one will be assigned if omitted)
	 * @return The new organization in DTO form
	 */
	@Override
	public OrganizationDto createOrganization(OrganizationDto organization) {
		OrganizationDto result = this.persistOrganization(organization);

		OrganizationChangedMessage message = new OrganizationChangedMessage();
		message.setOrgIds(Sets.newHashSet(result.getId()));
		eventManagerService.recordEventAndPublish(message);

		return result;
	}

	/**
	 * Updates an existing organization
	 *
	 * @param id           UUID of the existing organization
	 * @param organization The organization information to overwrite the existing with (in DTO form)
	 * @return The modified organization re-wrapped in a DTO object
	 */
	@Override
	public OrganizationDto updateOrganization(UUID id, OrganizationDto organization) {
		Organization entity = this.convertToEntity(organization);

		if (!id.equals(entity.getId()))
			throw new InvalidRecordUpdateRequest(String.format("ID: %s does not match the resource ID: %s", id, entity.getId()));

		Optional<Organization> dbEntity = repository.findById(id);

		if (dbEntity.isEmpty())
			throw new RecordNotFoundException(String.format(RESOURCE_NOT_FOUND_MSG, id));

		if (!orgChecksService.orgNameIsUnique(entity))
			throw new InvalidRecordUpdateRequest(String.format("Name: %s is already in use.", entity.getName()));

		// vet all this entity's desired subordinate organizations, make sure none of them are already in this entity's ancestry chain
		if (entity.getSubordinateOrganizations() != null && !entity.getSubordinateOrganizations().isEmpty()) {
			for (Organization subOrg : entity.getSubordinateOrganizations()) {
				if (orgIsInAncestryChain(subOrg.getId(), entity)) {
					throw new InvalidRecordUpdateRequest(String.format(ORG_IS_IN_ANCESTRY_MSG, subOrg.getId()));
				}
			}
		}

		OrganizationDto result = updateMetadata(entity, dbEntity, organization.getMeta());

		OrganizationChangedMessage message = new OrganizationChangedMessage();
		message.setOrgIds(Sets.newHashSet(result.getId()));
		eventManagerService.recordEventAndPublish(message);

		return result;
	}

	private OrganizationDto updateMetadata(Organization updatedEntity, Optional<Organization> dbEntity, Map<String, String> metadata) {
		checkValidMetadataProperties(updatedEntity.getOrgType(), metadata);
		List<OrganizationMetadata> toDelete = new ArrayList<>();
		if (dbEntity.isPresent()) {
			dbEntity.get().getMetadata().forEach(m -> {
				updatedEntity.getMetadata().add(m);
				if (metadata == null || metadata.containsKey(m.getKey())) {
					toDelete.add(m);
				}
			});
		}
		if (metadata != null) {
			metadata.forEach((key, value) -> {
				Optional<OrganizationMetadata> match = updatedEntity.getMetadata().stream().filter(x -> x.getKey() == key).findAny();
				if (match.isPresent()) {
					if (value == null) {
						toDelete.add(match.get());
					} else {
						match.get().setValue(value);
					}
				} else if (value != null) {
					updatedEntity.getMetadata().add(organizationMetadataRepository.save(new OrganizationMetadata(updatedEntity.getId(), key, value)));
				}
			});
		}
		OrganizationDto result = convertToDto(repository.save(updatedEntity));
		organizationMetadataRepository.deleteAll(toDelete);
		toDelete.forEach(m -> result.removeMetaProperty(m.getKey()));
		return result;
	}

	/**
	 * Deletes an organization by UUID (if it exists)
	 *
	 * @param id id of the Organization to delete
	 */
	@Override
	public void deleteOrganization(UUID id) {
		Organization org = repository.findById(id)
                .orElseThrow(() -> new RecordNotFoundException(String.format(RESOURCE_NOT_FOUND_MSG, id)));

        for (Person member : org.getPrimaryMembers()) {
            member.setPrimaryOrganization(null);
        }
        personRepository.saveAll(org.getPrimaryMembers());

		// must delete the parent link (since parent depends on this org to exist by its foreign key)
        if (org.getParentOrganization() != null) {
            org.setParentOrganization(null);
        }
        
        repository.save(org);

		freeOrganizationFromLinks(org);

		// now delete it
		repository.deleteById(id);

		// send the pub sub
		OrganizationDeleteMessage message = new OrganizationDeleteMessage();
		message.setOrgIds(Sets.newHashSet(id));
		eventManagerService.recordEventAndPublish(message);

	}

	/**
	 * Gets all organizations in the database, and converts them to DTO form before returning
	 *
	 * @param searchQuery String to search on the organization names
	 * @return Iterable of OrganizationDTOs
	 */
	@Override
	public Iterable<OrganizationDto> getOrganizations(String searchQuery, Pageable page) {
		return repository.findBy(page).stream()
				.filter(item -> item.getName().toLowerCase().contains(searchQuery.toLowerCase()))
				.map(this::convertToDto)
				.collect(Collectors.toList());
	}

	/**
	 * Gets a single Organization from the database by UUID
	 *
	 * @param id UUID of the organization to get
	 * @return The DTO representation of the organization
	 */
	@Override
	public OrganizationDto getOrganization(UUID id) {
		return this.convertToDto(this.findOrganization(id));
	}

	/**
	 * Adds one or more members from an org - this method is also used by child-types to add members
	 *
	 * @param organizationId The UUID of the organization to perform the operation on
	 * @param personIds      The list of UUIDs of type Person to add
	 * @return The updated OrganizationDTO object
	 */
	@Override
	public OrganizationDto addOrganizationMember(UUID organizationId, List<UUID> personIds, boolean primary) {
		return this.convertToDto(this.addMember(organizationId, personIds, primary));
	}

	/**
	 * Removes one or more members from an org - this method is also used by child-types to remove members
	 *
	 * @param organizationId The UUID of the organization to perform the operation on
	 * @param personIds      The list of UUIDs of type Person to remove
	 * @return The updated OrganizationDTO object
	 */
	@Override
	public OrganizationDto removeOrganizationMember(UUID organizationId, List<UUID> personIds) {
		return this.convertToDto(this.removeMember(organizationId, personIds));
	}

	/**
	 * Adds one or more subordinate organizations from provided organization
	 *
	 * @param organizationId The UUID of the organization to perform the operation on
	 * @param orgIds         The list of UUIDs of type Organization to add as subordinates
	 * @return The updated OrganizationDTO object
	 */
	@Override
	public OrganizationDto addSubordinateOrg(UUID organizationId, List<UUID> orgIds) {
		return this.convertToDto(this.addOrg(organizationId, orgIds));
	}

	/**
	 * Removes one or more subordinate organizations from provided organization
	 *
	 * @param organizationId The UUID of the organization to perform the operation on
	 * @param orgIds         The list of UUIDs of type Organization to removes from subordinates
	 * @return The updated OrganizationDTO object
	 */
	@Override
	public OrganizationDto removeSubordinateOrg(UUID organizationId, List<UUID> orgIds) {
		return this.convertToDto(this.removeOrg(organizationId, orgIds));
	}

	/**
	 * Adds a list of Org DTOs as new entities in the database.  Only fires one pub-sub event
	 * containing the UUIDs of the newly created Organizations.
	 *
	 * @param newOrgs List of Organization DTOs to add
	 * @return Same list of input Org DTOs (if they were all successfully created)
	 */
	@Override
	public List<OrganizationDto> bulkAddOrgs(List<OrganizationDto> newOrgs) {
		List<OrganizationDto> addedOrgs = new ArrayList<>();
		for (OrganizationDto org : newOrgs) {
			addedOrgs.add(this.persistOrganization(org));
		}

		// only send one pub-sub message for all added orgs (new org Ids will be an array in the message body)
		List<UUID> addedIds = addedOrgs.stream().map(OrganizationDto::getId).collect(Collectors.toList());
		OrganizationChangedMessage message = new OrganizationChangedMessage();
		message.setOrgIds(Sets.newHashSet(addedIds));
		eventManagerService.recordEventAndPublish(message);

		return addedOrgs;
	}

	/**
	 * Converts an organization entity with all its nested entities into a DTO
	 * structure with only UUID representations for types of Org/Person
	 * <p>
	 * Using model mapper here allows mapping autonomously of most fields except for the
	 * ones of custom type - where we have to explicitly grab out the UUID field since model
	 * mapper has no clue
	 *
	 * @param org
	 * @return object of type OrganizationTerseDto
	 */
	@Override
	public OrganizationDto convertToDto(Organization org) {
		modelMapper.getConfiguration().setPropertyCondition(Conditions.isNotNull());
		OrganizationDto dto = modelMapper.map(org, OrganizationDto.class);
		if (org.getMetadata() != null) {
			org.getMetadata().stream().forEach(m -> dto.setMetaProperty(m.getKey(), m.getValue()));
		}
		return dto;
	}

	/**
	 * Converts an organizational DTO back to its full POJO/entity
	 * It requires the reverse mapping of convertToDto process where we have to detect
	 * UUIDs and then go to the respective service to find them/look them up
	 *
	 * @param dto
	 * @return object of type Organization
	 */
	@Override
	public Organization convertToEntity(OrganizationDto dto) {

		Converter<UUID, Person> personDemapper = new AbstractConverter<UUID, Person>() {
			@Override
			protected Person convert(UUID uuid) {
				return personService.getPerson(uuid);
			}
		};

		Converter<UUID, Organization> orgDemapper = new AbstractConverter<UUID, Organization>() {
			@Override
			protected Organization convert(UUID uuid) {
				return findOrganization(uuid);
			}
		};

		try {
			modelMapper.getConfiguration().setPropertyCondition(Conditions.isNotNull());
			modelMapper.addConverter(personDemapper);
			modelMapper.addConverter(orgDemapper);
			Organization org = modelMapper.map(dto, Organization.class);

			// since model mapper has trouble mapping over UUID <--> Org for the nested Set<> in the Entity
			//  just iterate over and do the lookup manually
			if (dto.getSubordinateOrganizations() != null) {
				// Convert to set to remove duplicates
				Set<UUID> subOrgSet = new HashSet<>(dto.getSubordinateOrganizations());
				
				for (UUID id : subOrgSet) {
					org.addSubordinateOrganization(findOrganization(id));
				}
			}

			// since model mapper has trouble mapping over UUID <--> Person for the nested Set<> in the Entity
			//  just iterate over and do the lookup manually
			if (dto.getMembers() != null) {
				// Convert to set to remove duplicates
				Set<UUID> memberSet = new HashSet<>(dto.getMembers());
				
				for (UUID id : memberSet) {
					org.addMember(personService.getPerson(id));
				}
			}

			return org;
		}
		catch (Exception e) {
			throw e;
		}
	}

	/**
	 * Helper function that checks if a given org id is in the parental ancestry chain
	 * Primarily used before assigning an orgId as another org's subordinate org
	 * (example - Org A can't be an org B's subordinate if Org A already upstream of Org B)
	 * @param id          the org to check/vet is not already in the parental ancestry chain
	 * @param startingOrg the org to start the upward-search from
	 * @return true/false if 'id' is in the ancestry chain
	 */
	@Override
	public boolean orgIsInAncestryChain(UUID id, Organization startingOrg) {
		Organization parentOrg = startingOrg.getParentOrganization();
		if (parentOrg == null) return false;
		else if (parentOrg.getId().equals(id)) return true;
		else return orgIsInAncestryChain(id, parentOrg);
	}

	/**
	 * Helper function that checks if a given org parent candidate is already in the chosen org's
	 * descendents (downstream of it).
	 * Primarily used before assigning an org as a parent
	 * (example - Org A can't be an org B's parent if Org A already downstream of Org B)
	 * @param org          the org who wants to get a parent assigned to it
	 * @param candidateParentId the org to start the downward-search from
	 * @return true/false if 'id' is in the ancestry chain
	 */
	@Override
	public boolean parentOrgCandidateIsDescendent(OrganizationDto org, UUID candidateParentId) {
		return flattenOrg(org).getSubordinateOrganizations().contains(candidateParentId);
	}

	@Override
	public OrganizationDto flattenOrg(OrganizationDto org) {
		OrganizationDto flattenedOrg = new OrganizationDto();

		// copy over the basic info first
		flattenedOrg.setBranchType(org.getBranchType());
		flattenedOrg.setOrgType(org.getOrgType());
		flattenedOrg.setParentOrganizationUUID(org.getParentOrganization());
		flattenedOrg.setId(org.getId());
		flattenedOrg.setLeaderUUID(org.getLeader());
		flattenedOrg.setName(org.getName());
		flattenedOrg.setSubOrgsUUID(new ArrayList<>(harvestOrgSubordinateUnits(new HashSet<>(org.getSubordinateOrganizations()), new HashSet<>())));
		if (org.getMembers() != null) {
			flattenedOrg.setMembersUUID(new ArrayList<>(org.getMembers()));
		}
		else {
			flattenedOrg.setMembersUUID(new ArrayList<>());
		}
		flattenedOrg.setMembersUUID(harvestOrgMembers(new HashSet<>(org.getSubordinateOrganizations()), flattenedOrg.getMembers()));
		return flattenedOrg;
	}

	// recursive helper function to dig deep on a units subordinates
	private Set<UUID> harvestOrgSubordinateUnits(Set<UUID> orgIds, Set<UUID> accumulator) {

		if (orgIds == null || orgIds.isEmpty()) return accumulator;

		for (UUID orgId : orgIds) {
			accumulator.add(orgId);
			Set<UUID> ids = harvestOrgSubordinateUnits(new HashSet<>(getOrganization(orgId).getSubordinateOrganizations()), new HashSet<>());
			accumulator.addAll(ids);
		}

		return accumulator;
	}

	// recursive helper function to dig deep on a units members
	private List<UUID> harvestOrgMembers(Set<UUID> orgIds, List<UUID> accumulator) {

		if (orgIds == null || orgIds.isEmpty()) return accumulator;

		for (UUID orgId : orgIds) {
			OrganizationDto subOrg = getOrganization(orgId);
			if (subOrg.getLeader() != null) accumulator.add(subOrg.getLeader());  // make sure to roll up the leader if there is one
			if (subOrg.getMembers() != null) accumulator.addAll(subOrg.getMembers());
			List<UUID> ids = harvestOrgMembers(new HashSet<>(getOrganization(orgId).getSubordinateOrganizations()), new ArrayList<>());
			accumulator.addAll(ids);
		}

		return accumulator;
	}

	/**
	 * Private helper to make sure prior to setting an orgs parent, that that propose parent
	 * is not already in the descendents of said organization.
	 * @param org the org we're modifying the parent for
	 * @param parentUUIDCandidate the UUID of the proposed parent
	 */
	private void setOrgParentConditionally(Organization org, String parentUUIDCandidate) {

		// if we're setting to just null, skip all checks below
		if (parentUUIDCandidate == null) {
			org.setParentOrganization(null);
			repository.save(org);
			return;
		}

		if (!this.parentOrgCandidateIsDescendent(
				convertToDto(org), UUID.fromString(parentUUIDCandidate))) {

			Organization parentOrg = repository.findById(UUID.fromString(parentUUIDCandidate)).orElseThrow(
					() -> new InvalidRecordUpdateRequest("Provided org UUID " + parentUUIDCandidate + " was not found"));
			org.setParentOrganization(parentOrg);
			repository.save(org);
		}
		else {
			throw new InvalidRecordUpdateRequest("Proposed Parent UUID is already a descendent of this organization");
		}
	}

	/**
	 * Customizes the returned org entity by accepting fields from Controller's query params.
	 * Normally a user just gets a DTO back with UUIDs for nested members and orgs... this allows
	 * to specify fields to include on top of the UUIDs.  Nested members and organizations are never
	 * allowed to have their own subordinate organizations and members since this could be an infinite
	 * recursion.
	 *
	 * @param fields Map passed in from Controller with two keys - "people" and "orgs", each having a comma
	 *               separated list of field names to include for those types
	 * @param dto    The DTO to perform customization on
	 * @return The customized entity as a JsonNode blob to be returned by the controller
	 */
	@Override
	public JsonNode customizeEntity(Map<String, String> fields, OrganizationDto dto) {

		Organization org = convertToEntity(dto);
		ObjectMapper mapper = new ObjectMapper();
		mapper.addMixIn(Person.class, CustomPersonDtoMixin.class);
		mapper.addMixIn(Organization.class, CustomOrganizationDtoMixin.class);


		Set<String> mainEntityFilterFields = new HashSet<>();
		mainEntityFilterFields.add(OrganizationDto.MEMBERS_FIELD);
		mainEntityFilterFields.add(OrganizationDto.LEADER_FIELD);
		mainEntityFilterFields.add(OrganizationDto.SUB_ORGS_FIELD);
		mainEntityFilterFields.add(OrganizationDto.PARENT_ORG_FIELD);
		FilterProvider filters = new SimpleFilterProvider()
				.addFilter("orgFilter", SimpleBeanPropertyFilter
						.serializeAllExcept(mainEntityFilterFields));

		try {

			// filter out the fields having objects, we want everything else but those
			JsonNode mainNode = mapper.readTree(mapper.writer(filters).writeValueAsString(org));

			// condition the Person type fields the user gave
			Set<String> personEntityFilterFields = Arrays.stream(fields.get(OrganizationController.PEOPLE_PARAMS_FIELD)
					.split(","))
					.map(String::trim)
					.collect(Collectors.toSet());

			personEntityFilterFields.removeIf(s -> s.equals(""));
			if (personEntityFilterFields.isEmpty()) {
				personEntityFilterFields.add("id"); // add ID as the bare minimum like it would be on a regular DTO return
			}

			// condition the fields user gave
			Set<String> subOrgEntityFilterFields = Arrays.stream(fields.get(OrganizationController.ORGS_PARAMS_FIELD)
					.split(","))
					.map(String::trim)
					.collect(Collectors.toSet());

			subOrgEntityFilterFields.removeIf(s -> s.equals(""));
			if (subOrgEntityFilterFields.isEmpty()) {
				subOrgEntityFilterFields.add("id"); // add ID as the bare minimum like it would be on a regular DTO return
			}
			subOrgEntityFilterFields.remove(OrganizationDto.PARENT_ORG_FIELD);  // never allow parentOrg to be serialized on subordinate entities, might be infinite recursion
			subOrgEntityFilterFields.remove(OrganizationDto.SUB_ORGS_FIELD);  // never allow subordinateOrgs to be serialized inside subordinateOrgs, might be infinite recursion

			// add in the filters with the fields user gave to explicitly include
			filters = new SimpleFilterProvider()
					.addFilter("personFilter", SimpleBeanPropertyFilter
							.filterOutAllExcept(personEntityFilterFields))
					.addFilter("orgFilter", SimpleBeanPropertyFilter
							.filterOutAllExcept(subOrgEntityFilterFields));

			// json-ize the individual Person-type fields and Org-type fields
			JsonNode usersNode = mapper.readTree(mapper.writer(filters).writeValueAsString(org.getMembers()));
			JsonNode leaderNode = mapper.readTree(mapper.writer(filters).writeValueAsString(org.getLeader()));
			JsonNode parentOrgNode = mapper.readTree(mapper.writer(filters).writeValueAsString(org.getParentOrganization()));
			JsonNode subOrgsNode = mapper.readTree(mapper.writer(filters).writeValueAsString(org.getSubordinateOrganizations()));

			// reassemble the object that user will get
			((ObjectNode) mainNode).set(OrganizationDto.MEMBERS_FIELD, usersNode);
			((ObjectNode) mainNode).set(OrganizationDto.LEADER_FIELD, leaderNode);
			((ObjectNode) mainNode).set(OrganizationDto.PARENT_ORG_FIELD, parentOrgNode);
			((ObjectNode) mainNode).set(OrganizationDto.SUB_ORGS_FIELD, subOrgsNode);

			return mainNode;

		} catch (JsonProcessingException e) {
			throw new BadRequestException("Could not compile custom organizational entity");
		}
	}

	@Override
	public OrganizationDto applyPatchToOrganization(JsonPatch patch, OrganizationDto organizationDto) {
		try {
			JsonNode patched = patch.apply(this.objMapper.convertValue(organizationDto, JsonNode.class));
			return this.objMapper.treeToValue(patched, OrganizationDto.class);
		}
		catch (JsonPatchException | JsonProcessingException e) {
			throw new InvalidRecordUpdateRequest(String.format("Error patching organization %s.",
					organizationDto.getId()));
		}
	}

	private void checkValidMetadataProperties(Unit orgType, Map<String, String> metadata) {
		if (metadata != null) {
			if (orgType == null) {
				orgType = Unit.ORGANIZATION;
			}
			Set<String> properties = validProperties.get(orgType);
			Set<String> unknownProperties = new HashSet<>();
			metadata.forEach((key, value) -> {
				if (!properties.contains(key)) {
					unknownProperties.add(key);
				}
			});
			if (!unknownProperties.isEmpty()) {
				throw new InvalidRecordUpdateRequest(String.format("Invalid properties for %s: %s", orgType, String.join(", ", unknownProperties)));
			}
		}
	}

	/**
	 * Private helper to make sure org specified by 'id' is not bound or referenced
	 * by any other organization (used just prior to deletion).  If a link is found,
	 * it is removed.
	 * @param org organization to check for links to
	 */
	private void freeOrganizationFromLinks(Organization org) {

		List<Organization> parentalOrgs = repository.findOrganizationsByParentOrganization(org);
		for (Organization child: parentalOrgs) {
			child.setParentOrganization(null);
			repository.save(child);
		}

		List<Organization> orgsThatOwnThisOrg = repository.findOrganizationsBySubordinateOrganizationsContaining(org);
		for (Organization parent: orgsThatOwnThisOrg) {
			parent.removeSubordinateOrganization(org);
			repository.save(parent);
		}
	}

	/**
	 * Searches all organizations that have a leader by given UUID and removes them.
	 * Used by the Person service to remove hard links to a Person entity before deletion.
	 * @param leaderUuid id of the leader to remove from leader position(s)
	 */
	public void removeLeaderByUuid(UUID leaderUuid) {
		Person leaderPerson = personRepository.findById(leaderUuid)
				.orElseThrow(() -> new RecordNotFoundException("Leader person with ID " + leaderUuid + " not found"));

		List<Organization> modifiedOrgs = repository.findOrganizationsByLeader(leaderPerson);
		for (Organization org : modifiedOrgs) {
			org.setLeaderAndUpdateMembers(null);
			repository.save(org);
		}

		List<UUID> modifiedIds = modifiedOrgs.stream().map(Organization::getId).collect(Collectors.toList());

		OrganizationChangedMessage message = new OrganizationChangedMessage();
		message.setOrgIds(Sets.newHashSet(modifiedIds));
		eventManagerService.recordEventAndPublish(message);
	}

	@Override
	public Page<OrganizationDto> getOrganizationsPage(String searchQuery, Pageable page) {
		Page<Organization> pageResponse = repository.findAllByNameContainsIgnoreCase(searchQuery, page);
		
		return pageResponse.map(this::convertToDto);
	}

	@Override
	public Slice<OrganizationDto> getOrganizationsSlice(String searchQuery, Pageable page) {
		Slice<Organization> sliceResponse = repository.findByNameContainsIgnoreCase(searchQuery, page);
		
		return sliceResponse.map(this::convertToDto);
	}

	@Override
	public Page<Organization> findOrganizationsByTypeAndServicePage(String searchQuery, Unit type, Branch branch,
			Pageable page) {
		if (type == null && branch == null) {
			return repository.findAllByNameContainsIgnoreCase(searchQuery, page);
		}
		
		if (type != null && branch != null) {
			return repository.findAllByNameContainsIgnoreCaseAndOrgTypeAndBranchType(searchQuery, type, branch, page);
		}
		
		if (type != null) {
			return repository.findAllByNameContainsIgnoreCaseAndOrgType(searchQuery, type, page);
		} else {
			return repository.findAllByNameContainsIgnoreCaseAndBranchType(searchQuery, branch, page);
		}
	}

	@Override
	public Page<OrganizationDto> getOrganizationsByTypeAndServicePage(String searchQuery, Unit type, Branch branch,
			Pageable page) {
		return findOrganizationsByTypeAndServicePage(searchQuery, type, branch, page).map(this::convertToDto);
	}

	@Override
	public Slice<Organization> findOrganizationsByTypeAndServiceSlice(String searchQuery, Unit type, Branch branch,
			Pageable page) {
		if (type == null && branch == null) {
			return repository.findByNameContainsIgnoreCase(searchQuery, page);
		}
		
		if (type != null && branch != null) {
			return repository.findByNameContainsIgnoreCaseAndOrgTypeAndBranchType(searchQuery, type, branch, page);
		}
		
		if (type != null) {
			return repository.findByNameContainsIgnoreCaseAndOrgType(searchQuery, type, page);
		} else {
			return repository.findByNameContainsIgnoreCaseAndBranchType(searchQuery, branch, page);
		}
	}

	@Override
	public Slice<OrganizationDto> getOrganizationsByTypeAndServiceSlice(String searchQuery, Unit type, Branch branch,
			Pageable page) {
		return findOrganizationsByTypeAndServiceSlice(searchQuery, type, branch, page).map(this::convertToDto);
	}

	@Override
	public Page<OrganizationDto> getOrganizationsPageSpec(List<FilterCriteria> filterCriteria, Pageable page) {
		/**
		 * Transforms criteria for fields to account for join attributes.
		 * Takes the name of the field from the DTO and transforms
		 * the criteria to use the field name from the entity.
		 * 
		 * EX: rank field on PersonDto corresponds to the string Abbreviation field of Rank
		 */
		filterCriteria = filterCriteria.stream().map(criteria -> {
			switch (criteria.getField()) {
				case OrganizationDto.PARENT_ORG_FIELD:
					criteria.transformToJoinAttribute(Organization.ID_FIELD, Organization.PARENT_ORG_FIELD);
					break;
					
				case OrganizationDto.SUB_ORGS_FIELD:
					criteria.transformToJoinAttribute(Organization.ID_FIELD, Organization.SUB_ORGS_FIELD);
					break;
					
				case OrganizationDto.MEMBERS_FIELD:
					criteria.transformToJoinAttribute(Person.ID_FIELD, Organization.MEMBERS_FIELD);
					break;
					
				case OrganizationDto.LEADER_FIELD:
					criteria.transformToJoinAttribute(Person.ID_FIELD, Organization.LEADER_FIELD);
					break;
					
				default:
					break;
			}
				
			return criteria;
		}).collect(Collectors.toList());
		
		Specification<Organization> spec = SpecificationBuilder.getSpecificationFromFilters(filterCriteria);
		Page<Organization> pagedResponse = repository.findAll(spec, page);
		
		return pagedResponse.map(this::convertToDto);
	}
}
