package mil.tron.commonapi.service;

import mil.tron.commonapi.dto.OrganizationDto;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.orgtypes.Unit;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.OrganizationRepository;
import mil.tron.commonapi.repository.PersonRepository;
import mil.tron.commonapi.service.utility.OrganizationUniqueChecksService;
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
	private final DtoMapper modelMapper;
	private static final String RESOURCE_NOT_FOUND_MSG = "Resource with the ID: %s does not exist.";
	
	public OrganizationServiceImpl(
			OrganizationRepository repository,
			PersonRepository personRepository,
			PersonService personService,
			OrganizationUniqueChecksService orgChecksService) {

		this.repository = repository;
		this.personRepository = personRepository;
		this.personService = personService;
		this.orgChecksService = orgChecksService;
		this.modelMapper = new DtoMapper();
	}

	/**
	 * Finds a record by UUID that returns the raw entity type (not DTO)
	 * @param id UUID of the organization to find
	 * @return Organization entity object (if found), otherwise throws Exception
	 */
	@Override
	public Organization findOrganization(UUID id) {
		return repository.findById(id).orElseThrow(() -> new RecordNotFoundException(String.format(RESOURCE_NOT_FOUND_MSG, id)));
	}

	/**
	 * Adds a list of organizations as subordinate orgs to provided organization
	 * @param organizationId organization ID to modify
	 * @param orgIds list of orgs by their UUIDs to add as subordinate organizations
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
			}
			else {
				throw new InvalidRecordUpdateRequest("Organization " + subordinate.getId() + " is already an ancestor to this org!");
			}
		}

		return repository.save(organization);
	}

	/**
	 * Removes a list of organizations as subordinate orgs from provided organization
	 * @param organizationId organization ID to modify
	 * @param orgIds list of orgs by their UUIDs to remove from subordinate organizations
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
	 * @param organizationId UUID of the organization
	 * @param personIds List of Person UUIDs to remove
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
	 * @param organizationId UUID of the organization
	 * @param personIds List of Person UUIDs to remove
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
	 * @param organizationId The UUID of the organization to modify
	 * @param attribs A map of string key-values where keys are named of fields and values are the value to set to
	 * @return The modified Organization entity object
	 */
	@Override
	public Organization modify(UUID organizationId, Map<String, String> attribs) {
		Organization organization = repository.findById(organizationId).orElseThrow(
				() -> new RecordNotFoundException(String.format(RESOURCE_NOT_FOUND_MSG, organizationId.toString())));

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

				}
			}
			catch (NoSuchMethodException e) {
				throw new InvalidRecordUpdateRequest("Provided field: " + field.getName() + " is not settable");
			}
		});

		return repository.save(organization);
	}

	/**
	 * Filters out organizations by type and branch.
	 * @param searchQuery name of org to match on for filtering (case in-sensitve)
	 * @param type The unit type
	 * @param branch The branch/service type (if null then ignores it)
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
	 * @param searchQuery name of org to match on for filtering (case in-sensitve)
	 * @param type The unit type
	 * @param branch The branch service type (null to ignore)
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
					throw new InvalidRecordUpdateRequest("Organization " + subOrg.getId() + " is already an ancestor to this org!");
				}
			}
		}
		
		return this.convertToDto(repository.save(org));
	}

	/**
	 * Updates an existing organization
	 * @param id UUID of the existing organization
	 * @param organization The organization information to overwrite the existing with (in DTO form)
	 * @return The modified organization re-wrapped in a DTO object
	 */
	@Override
	public OrganizationDto updateOrganization(UUID id, OrganizationDto organization) {
		Organization org = this.convertToEntity(organization);

		if (!id.equals(org.getId()))
			throw new InvalidRecordUpdateRequest(String.format("ID: %s does not match the resource ID: %s", id, org.getId()));
		
		Optional<Organization> dbOrg = repository.findById(id);
		
		if (dbOrg.isEmpty())
			throw new RecordNotFoundException(String.format(RESOURCE_NOT_FOUND_MSG, id));
		
		if (!orgChecksService.orgNameIsUnique(org))
			throw new InvalidRecordUpdateRequest(String.format("Name: %s is already in use.", org.getName()));

		// vet all this org's desired subordinate organizations, make sure none of them are already in this org's ancestry chain
		if (org.getSubordinateOrganizations() != null && !org.getSubordinateOrganizations().isEmpty()) {
			for (Organization subOrg : org.getSubordinateOrganizations()) {
				if (orgIsInAncestryChain(subOrg.getId(), org)) {
					throw new InvalidRecordUpdateRequest("Organization " + subOrg.getId() + " is already an ancestor to this org!");
				}
			}
		}
		
		return this.convertToDto(repository.save(org));
	}

	/**
	 * Deletes an organization by UUID (if it exists)
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
	 * @param personIds The list of UUIDs of type Person to add
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
	 * @param personIds The list of UUIDs of type Person to remove
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
	 * @param orgIds The list of UUIDs of type Organization to add as subordinates
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
	 * @param orgIds The list of UUIDs of type Organization to removes from subordinates
	 * @return The updated OrganizationDTO object
	 */
	@Override
	public OrganizationDto removeSubordinateOrg(UUID organizationId, List<UUID> orgIds) {
		return this.convertToDto(this.removeOrg(organizationId, orgIds));
	}

	/**
	 * Modifies an organization's attributes (except members) such as Leader, Parent Org, and Name
	 * @param organizationId UUID of the organization to modify
	 * @param attribs a HashMap of fields to change (in key/value form)
	 * @return the modified and persisted organization
	 */
	@Override
	public OrganizationDto modifyAttributes(UUID organizationId, Map<String, String> attribs) {
		return this.convertToDto(this.modify(organizationId, attribs));
	}

	/**
	 * Adds a list of Org DTOs as new entities in the database
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
	 *
	 * Using model mapper here allows mapping autonomously of most fields except for the
	 * ones of custom type - where we have to explicitly grab out the UUID field since model
	 * mapper has no clue
	 * @param org
	 * @return object of type OrganizationTerseDto
	 */
    @Override
    public OrganizationDto convertToDto(Organization org) {
		modelMapper.getConfiguration().setPropertyCondition(Conditions.isNotNull());
		return modelMapper.map(org, OrganizationDto.class);
    }

	/**
	 * Converts an organizational DTO back to its full POJO/entity
	 * It requires the reverse mapping of convertToDto process where we have to detect
	 * UUIDs and then go to the respective service to find them/look them up
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
	 * @param id the org to check/vet is not already in the parental ancestry chain
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
}
