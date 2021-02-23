package mil.tron.commonapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
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
import mil.tron.commonapi.repository.OrganizationMetadataRepository;
import mil.tron.commonapi.repository.OrganizationRepository;
import mil.tron.commonapi.repository.PersonRepository;
import mil.tron.commonapi.service.utility.OrganizationUniqueChecksService;
import static mil.tron.commonapi.service.utility.ReflectionUtils.*;
import org.modelmapper.AbstractConverter;
import org.modelmapper.Conditions;
import org.modelmapper.Converter;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class OrganizationServiceImpl implements OrganizationService {
	private final OrganizationRepository repository;
	private final PersonService personService;
	private final PersonRepository personRepository;
	private final OrganizationUniqueChecksService orgChecksService;
	private final OrganizationMetadataRepository organizationMetadataRepository;
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

	public OrganizationServiceImpl(
			OrganizationRepository repository,
			PersonRepository personRepository,
			PersonService personService,
			OrganizationUniqueChecksService orgChecksService,
			OrganizationMetadataRepository organizationMetadataRepository) {

		this.repository = repository;
		this.personRepository = personRepository;
		this.personService = personService;
		this.orgChecksService = orgChecksService;
		this.organizationMetadataRepository = organizationMetadataRepository;
		this.modelMapper = new DtoMapper();
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

		return repository.save(organization);
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

		return repository.save(organization);
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

		for (UUID id : personIds) {
			Person person = personRepository.findById(id).orElseThrow(
					() -> new InvalidRecordUpdateRequest(String.format(RESOURCE_NOT_FOUND_MSG, id)));

			organization.removeMember(person);
		}

		return repository.save(organization);
	}

	/**
	 * Adds members from an organization and re-persists it to db.
	 *
	 * @param organizationId UUID of the organization
	 * @param personIds      List of Person UUIDs to remove
	 * @return Organization entity object
	 */
	@Override
	public Organization addMember(UUID organizationId, List<UUID> personIds) {
		Organization organization = repository.findById(organizationId)
				.orElseThrow(() -> new RecordNotFoundException(
						String.format(RESOURCE_NOT_FOUND_MSG, organizationId.toString())));

		for (UUID id : personIds) {
			Person person = personRepository.findById(id).orElseThrow(
					() -> new InvalidRecordUpdateRequest("Provided person UUID " + id.toString() + " does not exist"));

			organization.addMember(person);
		}

		return repository.save(organization);
	}

	/**
	 * Modifies non collection type attributes of the organization
	 *
	 * @param organizationId The UUID of the organization to modify
	 * @param attribs        A map of string key-values where keys are named of fields and values are the value to set to
	 * @return The modified Organization entity object
	 */
	@Override
	public OrganizationDto modify(UUID organizationId, Map<String, String> attribs) {
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

	/**
	 * Filters out organizations by type and branch.
	 *
	 * @param searchQuery name of org to match on for filtering (case in-sensitve)
	 * @param type        The unit type
	 * @param branch      The branch/service type (if null then ignores it)
	 * @return filtered list of Organizations
	 */
	@Override
	public Iterable<Organization> findOrganizationsByTypeAndService(String searchQuery, Unit type, Branch branch) {
		return StreamSupport
				.stream(repository.findAll().spliterator(), false)
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
	public Iterable<OrganizationDto> getOrganizationsByTypeAndService(String searchQuery, Unit type, Branch branch) {
		return StreamSupport
				.stream(this.findOrganizationsByTypeAndService(searchQuery, type, branch).spliterator(), false)
				.map(this::convertToDto)
				.collect(Collectors.toList());
	}

	/**
	 * Creates a new organization and returns the DTO representation of which
	 *
	 * @param organization The DTO containing the new Org information with an optional UUID (one will be assigned if omitted)
	 * @return The new organization in DTO form
	 */
	@Override
	public OrganizationDto createOrganization(OrganizationDto organization) {
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

		if (organization.getMeta() != null) {
			organization.getMeta().forEach((key, value) ->
					org.getMetadata().add(new OrganizationMetadata(org.getId(), key, value))
			);
		}

		return this.convertToDto(repository.save(org));
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

		return updateMetadata(entity, dbEntity, organization.getMeta());
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
	 * @param id
	 */
	@Override
	public void deleteOrganization(UUID id) {
		if (repository.existsById(id))
			repository.deleteById(id);
		else
			throw new RecordNotFoundException(String.format(RESOURCE_NOT_FOUND_MSG, id));
	}

	/**
	 * Gets all organizations in the database, and converts them to DTO form before returning
	 *
	 * @param searchQuery String to search on the organization names
	 * @return Iterable of OrganizationDTOs
	 */
	@Override
	public Iterable<OrganizationDto> getOrganizations(String searchQuery) {
		return StreamSupport
				.stream(repository.findAll().spliterator(), false)
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
	public OrganizationDto addOrganizationMember(UUID organizationId, List<UUID> personIds) {
		return this.convertToDto(this.addMember(organizationId, personIds));
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
	 * Adds a list of Org DTOs as new entities in the database
	 *
	 * @param newOrgs List of Organization DTOs to add
	 * @return Same list of input Org DTOs (if they were all successfully created)
	 */
	@Override
	public List<OrganizationDto> bulkAddOrgs(List<OrganizationDto> newOrgs) {
		List<OrganizationDto> addedOrgs = new ArrayList<>();
		for (OrganizationDto org : newOrgs) {
			addedOrgs.add(this.createOrganization(org));
		}

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
		org.getMetadata().stream().forEach(m -> dto.setMetaProperty(m.getKey(), m.getValue()));
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

		modelMapper.getConfiguration().setPropertyCondition(Conditions.isNotNull());
		modelMapper.addConverter(personDemapper);
		modelMapper.addConverter(orgDemapper);
		Organization org = modelMapper.map(dto, Organization.class);

		// since model mapper has trouble mapping over UUID <--> Org for the nested Set<> in the Entity
		//  just iterate over and do the lookup manually
		if (dto.getSubordinateOrganizations() != null) {
			for (UUID id : dto.getSubordinateOrganizations()) {
				org.addSubordinateOrganization(findOrganization(id));
			}
		}

		// since model mapper has trouble mapping over UUID <--> Person for the nested Set<> in the Entity
		//  just iterate over and do the lookup manually
		if (dto.getMembers() != null) {
			for (UUID id : dto.getMembers()) {
				org.addMember(personService.getPerson(id));
			}
		}

		return org;
	}

	/**
	 * Helper function that checks if a given org id is in the parental ancestry chain
	 *
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
			if (unknownProperties.size() > 0) {
				throw new InvalidRecordUpdateRequest(String.format("Invalid properties for %s: %s", orgType, String.join(", ", unknownProperties)));
			}
		}
	}
}
