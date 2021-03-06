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
import mil.tron.commonapi.exception.NotAuthorizedException;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.exception.efa.IllegalOrganizationModification;
import mil.tron.commonapi.pubsub.EventManagerService;
import mil.tron.commonapi.pubsub.messages.*;
import mil.tron.commonapi.repository.OrganizationMetadataRepository;
import mil.tron.commonapi.repository.OrganizationRepository;
import mil.tron.commonapi.repository.PersonRepository;
import mil.tron.commonapi.repository.filter.FilterCriteria;
import mil.tron.commonapi.repository.filter.SpecificationBuilder;
import mil.tron.commonapi.service.fieldauth.EntityFieldAuthResponse;
import mil.tron.commonapi.service.fieldauth.EntityFieldAuthService;
import mil.tron.commonapi.service.fieldauth.EntityFieldAuthType;
import mil.tron.commonapi.service.utility.OrganizationUniqueChecksService;
import mil.tron.commonapi.service.utility.ValidatorService;

import org.modelmapper.AbstractConverter;
import org.modelmapper.Conditions;
import org.modelmapper.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.MethodArgumentNotValidException;

import javax.transaction.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static mil.tron.commonapi.service.utility.ReflectionUtils.checkNonPatchableFieldsUntouched;
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
	private final EntityFieldAuthService entityFieldAuthService;
	private final ValidatorService validatorService;
	private static final String RESOURCE_NOT_FOUND_MSG = "Resource with the ID: %s does not exist.";
	private static final String ORG_IS_IN_ANCESTRY_MSG = "Organization %s is already an ancestor to this organization.";
	private static final String ORG_IS_ALREADY_SUBORG_ELSEWHERE = "Organization %s is already a subordinate to another organization.";
	private static final String USER_NOT_AUTHORIZED_FIELD_EDIT_MSG = "Do not have the necessary privileges to edit the field [%s]";

	@Value("${efa-enabled}")
	private boolean efaEnabled;

	private static final Map<Unit, Set<String>> validProperties = Map.of(
			Unit.FLIGHT, fields(Flight.class),
			Unit.GROUP, fields(Group.class),
			Unit.OTHER_USAF, fields(OtherUsaf.class),
			Unit.SQUADRON, fields(Squadron.class),
			Unit.WING, fields(Wing.class),
			Unit.ORGANIZATION, Collections.emptySet()
	);

	private final ObjectMapper objMapper;

	@SuppressWarnings("squid:S00107")
	public OrganizationServiceImpl(
			OrganizationRepository repository,
			PersonRepository personRepository,
			PersonService personService,
			OrganizationUniqueChecksService orgChecksService,
			OrganizationMetadataRepository organizationMetadataRepository,
			EventManagerService eventManagerService,
			EntityFieldAuthService entityFieldAuthService,
			ValidatorService validatorService) {

		this.repository = repository;
		this.personRepository = personRepository;
		this.personService = personService;
		this.orgChecksService = orgChecksService;
		this.organizationMetadataRepository = organizationMetadataRepository;
		this.eventManagerService = eventManagerService;
		this.entityFieldAuthService = entityFieldAuthService;
		this.validatorService = validatorService;
		this.modelMapper = new DtoMapper();
		this.objMapper = new ObjectMapper();
	}

	// helper that applies entity field authorization for us
	private EntityFieldAuthResponse<Organization> applyFieldAuthority(Organization incomingEntity) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return entityFieldAuthService.adjudicateOrganizationFields(incomingEntity, authentication);
	}
	
	private boolean isUserAuthorizedForFieldEdit(String fieldName) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return entityFieldAuthService.userHasAuthorizationToField(authentication, EntityFieldAuthType.ORGANIZATION, fieldName);
	}

	private void performOrganizationParentChildLogic(Organization org) {
		// vet all this org's desired subordinate organizations, make sure none of them are already in this org's ancestry chain
		if (org.getSubordinateOrganizations() != null && !org.getSubordinateOrganizations().isEmpty()) {
			for (Organization subOrg : org.getSubordinateOrganizations()) {
				performFamilyTreeChecks(org, subOrg);
			}
		}
	}

	private void performParentChecks(Organization org) {
		String parentUUID = org.getParentOrganization() == null ? null : org.getParentOrganization().getId().toString();
		setOrgParentConditionally(org, parentUUID);
	}

	private void performFamilyTreeChecks(Organization org, Organization subOrg) {

		if (org == null) throw new BadRequestException("Organization being modified was null");
		if (subOrg == null) throw new BadRequestException("Subordinate Organization was null");

		if (orgIsInAncestryChain(subOrg.getId(), org)) {
			throw new InvalidRecordUpdateRequest(String.format(ORG_IS_IN_ANCESTRY_MSG, subOrg.getName()));
		}
		if (!repository.findOrganizationsBySubordinateOrganizationsContainingAndIdIsNot(subOrg, org.getId()).isEmpty()) {
			throw new InvalidRecordUpdateRequest(String.format(ORG_IS_ALREADY_SUBORG_ELSEWHERE, subOrg.getName()));
		}

		// modify the suborg's parent
		setOrgParentConditionally(subOrg, org.getId().toString());
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
	 * Will perform EFA check.
	 *
	 * @param organizationId organization ID to modify
	 * @param orgIds         list of orgs by their UUIDs to add as subordinate organizations
	 * @return the persisted, modified organization
	 * 
	 * @throws IllegalOrganizationModification throws if EFA denied fields. Will still save Organization.
	 */
	@Override
	@Transactional(dontRollbackOn={IllegalOrganizationModification.class})
	public OrganizationDto addOrg(UUID organizationId, List<UUID> orgIds) {
		Organization organization = repository.findById(organizationId)
				.orElseThrow(() -> new RecordNotFoundException(
						String.format(RESOURCE_NOT_FOUND_MSG, organizationId.toString())));

		for (UUID id : orgIds) {
			Organization subordinate = repository.findById(id).orElseThrow(
					() -> new InvalidRecordUpdateRequest(String.format(RESOURCE_NOT_FOUND_MSG, id)));

			performFamilyTreeChecks(organization, subordinate);
		}

		EntityFieldAuthResponse<Organization> efaResponse = applyFieldAuthority(organization);
		Organization result = repository.save(efaResponse.getModifiedEntity());

		SubOrgAddMessage message = new SubOrgAddMessage();
		message.setParentOrgId(organizationId);
		message.setSubOrgsAdded(Sets.newHashSet(orgIds));
		eventManagerService.recordEventAndPublish(message);

		return checkEfaResponseForIllegalModification(result, efaResponse.getDeniedFields());
	}

	/**
	 * Removes a list of organizations as subordinate orgs from provided organization.
	 * Will perform EFA check.
	 *
	 * @param organizationId organization ID to modify
	 * @param orgIds         list of orgs by their UUIDs to remove from subordinate organizations
	 * @return the persisted, modified organization
	 * 
	 * @throws IllegalOrganizationModification throws if EFA denied fields. Will still save Organization.
	 */
	@Override
	@Transactional(dontRollbackOn={IllegalOrganizationModification.class})
	public OrganizationDto removeOrg(UUID organizationId, List<UUID> orgIds) {
		Organization organization = repository.findById(organizationId)
				.orElseThrow(() -> new RecordNotFoundException(
						String.format(RESOURCE_NOT_FOUND_MSG, organizationId.toString())));

		for (UUID id : orgIds) {
			Organization subordinate = repository.findById(id).orElseThrow(
					() -> new InvalidRecordUpdateRequest(String.format(RESOURCE_NOT_FOUND_MSG, id)));

			organization.removeSubordinateOrganization(subordinate);

			// modify this suborg's parent - they were removed from being a suborg of some parent, so null out parent
			setOrgParentConditionally(subordinate, null);
			repository.save(subordinate);
		}

		EntityFieldAuthResponse<Organization> efaResponse = applyFieldAuthority(organization);
		Organization result = repository.save(efaResponse.getModifiedEntity());

		SubOrgRemoveMessage message = new SubOrgRemoveMessage();
		message.setParentOrgId(organizationId);
		message.setSubOrgsRemoved(Sets.newHashSet(orgIds));
		eventManagerService.recordEventAndPublish(message);

		return checkEfaResponseForIllegalModification(result, efaResponse.getDeniedFields());
	}

	/**
	 * Removes members from an organization and re-persists it to db.
	 * Will perform EFA check.
	 *
	 * @param organizationId UUID of the organization
	 * @param personIds      List of Person UUIDs to remove
	 * @return Organization entity object
	 * 
	 * @throws IllegalOrganizationModification throws if EFA denied fields. Will still save Organization.
	 */
	@Override
	@Transactional(dontRollbackOn={IllegalOrganizationModification.class})
	public OrganizationDto removeMember(UUID organizationId, List<UUID> personIds) {
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

		EntityFieldAuthResponse<Organization> efaResponse = applyFieldAuthority(organization);
		Organization result = repository.save(efaResponse.getModifiedEntity());

        if (!updatedPersons.isEmpty()) {
            personRepository.saveAll(updatedPersons);
        }

		PersonOrgRemoveMessage message = new PersonOrgRemoveMessage();
		message.setParentOrgId(organizationId);
		message.setMembersRemoved(Sets.newHashSet(personIds));
		eventManagerService.recordEventAndPublish(message);

		return checkEfaResponseForIllegalModification(result, efaResponse.getDeniedFields());
	}

	/**
     * Adds members from an organization and re-persists it to db.
     *
     * @param organizationId UUID of the organization
     * @param personIds      List of Person UUIDs to remove
     * @param primary        Whether to set the org as the persons' primary org
     * @return Organization entity object
     * 
     * @throws IllegalOrganizationModification throws if EFA has denied fields. Will still saved the updated organization.
     * 
     */
	@Override
	@Transactional(dontRollbackOn={IllegalOrganizationModification.class})
	public OrganizationDto addMember(UUID organizationId, List<UUID> personIds, boolean primary) {
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

        EntityFieldAuthResponse<Organization> efaResponse = applyFieldAuthority(organization);
		Organization result = repository.save(efaResponse.getModifiedEntity());
        
        if (primary) {
            personRepository.saveAll(updatedPersons);
        }

		PersonOrgAddMessage message = new PersonOrgAddMessage();
		message.setParentOrgId(organizationId);
		message.setMembersAdded(Sets.newHashSet(personIds));
		eventManagerService.recordEventAndPublish(message);

		return checkEfaResponseForIllegalModification(result, efaResponse.getDeniedFields());
	}

	/**
	 * Modifies non collection type attributes of the organization.
	 * EFA will be applied to the modified organization.
	 *
	 * @param organizationId The UUID of the organization to modify
	 * @param attribs        A map of string key-values where keys are named of fields and values are the value to set to
	 * @return The modified Organization entity object
	 * 
	 * @throws IllegalOrganizationModification throws if EFA has denied some fields. Organization will still be saved with fields that were not denied.
	 */
	@Override
	@Transactional(dontRollbackOn={IllegalOrganizationModification.class})
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

		Organization result = appendAndUpdateMetadata(organization, Optional.empty(), metadata, isUserAuthorizedForFieldEdit(Organization.METADATA_FIELD));
		
		EntityFieldAuthResponse<Organization> efaResponse = applyFieldAuthority(result);
		
		performOrganizationParentChildLogic(result);
		performParentChecks(result);
		
		Organization savedResult = repository.save(efaResponse.getModifiedEntity());
		return checkEfaResponseForIllegalModification(savedResult, efaResponse.getDeniedFields());
	}

	/**
	 * Patched Organization will have EFA applied.
	 * 
	 * @throws IllegalOrganizationModification throws if EFA failed on some fields. The updated Organization will still be saved.
	 */
	@Override
	@Transactional(dontRollbackOn={IllegalOrganizationModification.class})
	public OrganizationDto patchOrganization(UUID id, JsonPatch patch) throws MethodArgumentNotValidException {
		Optional<Organization> dbOrganization = this.repository.findById(id);

		if (dbOrganization.isEmpty()) {
			throw new RecordNotFoundException(String.format("Organization %s not found.", id));
		}

		OrganizationDto dbOrgDto = convertToDto(dbOrganization.get());
		OrganizationDto patchedOrgDto = applyPatchToOrganization(patch, dbOrgDto);

		// check we didnt change anything on any NonPatchableFields
		checkNonPatchableFieldsUntouched(dbOrgDto, patchedOrgDto);

		// Validate the dto with the changes applied to it
		validatorService.isValid(patchedOrgDto, OrganizationDto.class);

		Organization patchedOrg = convertToEntity(patchedOrgDto);

		// If patch changes name and the new name is not unique throw error
		if (!dbOrganization.get().getName().equalsIgnoreCase(patchedOrg.getName()) &&
			!this.orgChecksService.orgNameIsUnique(patchedOrg)) {
			throw new InvalidRecordUpdateRequest(String.format("Organization already exists with name %s",
					patchedOrg.getName()));
		}

		appendAndUpdateMetadata(patchedOrg, dbOrganization, patchedOrgDto.getMeta(), isUserAuthorizedForFieldEdit(Organization.METADATA_FIELD));
		
		EntityFieldAuthResponse<Organization> efaResponse = applyFieldAuthority(patchedOrg);
		
		performOrganizationParentChildLogic(efaResponse.getModifiedEntity());
		performParentChecks(efaResponse.getModifiedEntity());
		
		Organization result = repository.save(efaResponse.getModifiedEntity());

		OrganizationChangedMessage message = new OrganizationChangedMessage();
		message.addOrgId(id);
		eventManagerService.recordEventAndPublish(message);

		return checkEfaResponseForIllegalModification(result, efaResponse.getDeniedFields());
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
	public OrganizationDto persistOrganization(OrganizationDto organization) {
		Organization org = this.convertToEntity(organization);

		if (repository.existsById(org.getId()))
			throw new ResourceAlreadyExistsException(String.format("Resource with the ID: %s already exists.", org.getId()));

		if (!orgChecksService.orgNameIsUnique(org))
			throw new ResourceAlreadyExistsException(String.format("Resource with the Name: %s already exists.", org.getName()));

		// go ahead and save the org's name so its at least in the db, should anything fail later on it will get rolled back out
		repository.save(Organization
				.builder()
				.id(organization.getId())
				.name(organization.getName())
				.build());

		performOrganizationParentChildLogic(org);
		performParentChecks(org);

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
	@Transactional
	public OrganizationDto createOrganization(OrganizationDto organization) {
		OrganizationDto result = this.persistOrganization(organization);

		OrganizationChangedMessage message = new OrganizationChangedMessage();
		message.setOrgIds(Sets.newHashSet(result.getId()));
		eventManagerService.recordEventAndPublish(message);

		return result;
	}

	/**
	 * Updates an existing organization. EFA will be performed on the updated Organization.
	 *
	 * @param id           UUID of the existing organization
	 * @param organization The organization information to overwrite the existing with (in DTO form)
	 * @return The modified organization re-wrapped in a DTO object
	 * 
	 * @throws IllegalOrganizationModification throws if EFA failed on some fields. The updated Organization will be saved even if EFA has failed
	 */
	@Override
	@Transactional(dontRollbackOn={IllegalOrganizationModification.class})
	public OrganizationDto updateOrganization(UUID id, OrganizationDto organization) {
		Organization entity = this.convertToEntity(organization);

		if (!id.equals(entity.getId()))
			throw new InvalidRecordUpdateRequest(String.format("ID: %s does not match the resource ID: %s", id, entity.getId()));

		Optional<Organization> dbEntity = repository.findById(id);

		if (dbEntity.isEmpty())
			throw new RecordNotFoundException(String.format(RESOURCE_NOT_FOUND_MSG, id));

		if (!orgChecksService.orgNameIsUnique(entity))
			throw new InvalidRecordUpdateRequest(String.format("Name: %s is already in use.", entity.getName()));
		
		appendAndUpdateMetadata(entity, dbEntity, organization.getMeta(), isUserAuthorizedForFieldEdit(Organization.METADATA_FIELD));
		
		EntityFieldAuthResponse<Organization> efaResponse = applyFieldAuthority(entity);
		
		performOrganizationParentChildLogic(efaResponse.getModifiedEntity());
		performParentChecks(efaResponse.getModifiedEntity());
		
		Organization result = repository.save(efaResponse.getModifiedEntity());
		
		OrganizationChangedMessage message = new OrganizationChangedMessage();
		message.setOrgIds(Sets.newHashSet(result.getId()));
		eventManagerService.recordEventAndPublish(message);
		
		return checkEfaResponseForIllegalModification(result, efaResponse.getDeniedFields());
	}
	
	/**
	 * Appends any metadata from {@code dbEntity} to {@code updatedEntity}
	 * along with any modifications that arise as a result of {@code metadata}.
	 * Metadata changes will only be applied to {@link #organizationMetadataRepository}
	 * if {@code allowedToEdit} is equal to {@code true}.
	 * 
	 * This action will not modify {@code dbEntity} in any manner.
	 * 
	 * @param updatedEntity the modified entity
	 * @param dbEntity the database value of {@code updatedEntity}
	 * @param metadata metadata to modify
	 * @param allowedToEdit if requesting user is allowed to edit metadata
	 * @return {@code updatedEntity} with metadata modifications applied
	 */
	private Organization appendAndUpdateMetadata(Organization updatedEntity, Optional<Organization> dbEntity, Map<String, String> metadata, boolean allowedToEdit) {
		checkValidMetadataProperties(updatedEntity.getOrgType(), metadata);
		List<OrganizationMetadata> toDelete = new ArrayList<>();
		List<OrganizationMetadata> toSave = new ArrayList<>();
		
		updatedEntity.getMetadata().clear();
		
		// Metadata is null, so add everything to delete
		if (metadata == null) {
			dbEntity.ifPresent(entity -> toDelete.addAll(entity.getMetadata()));
		} else {
			if (dbEntity.isEmpty()) {
				metadata.forEach((key, value) -> {
					OrganizationMetadata meta = OrganizationMetadata.builder()
							.organizationId(updatedEntity.getId())
							.key(key)
							.value(value)
							.build();
					
					toSave.add(meta);
				});
			} else {
				metadata.forEach((key, value) -> {
					Optional<OrganizationMetadata> match = dbEntity.get().getMetadata().stream().filter(x -> x.getKey().equals(key)).findAny();
					
					// When this metadata value exist on the database entity
					// and it's value is null, then this entry is up for deletion.
					// Otherwise it is either a new entry or an update to an
					// existing entry, so it's up for save.
					if (match.isPresent() && value == null) {
						toDelete.add(match.get());
					} else {
						OrganizationMetadata meta = OrganizationMetadata.builder()
								.organizationId(updatedEntity.getId())
								.key(key)
								.value(value)
								.build();
						
						toSave.add(meta);
					}
				});
			}
		}
		
		// Only send these changes to the database if the requesting
		// user is allowed to edit. Otherwise metadata changes updated
		// on updatedEntity are only appended to reflect a change.
		if (allowedToEdit) {
			organizationMetadataRepository.deleteAll(toDelete);
			organizationMetadataRepository.saveAll(toSave);
		}
		
		updatedEntity.getMetadata().addAll(toSave);
		
		return updatedEntity;
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
	 * 
	 * @throws NotAuthorizedException throws if requesting user does not have privilege to edit field
	 */
	@Override
	@Transactional(dontRollbackOn={IllegalOrganizationModification.class})
	public OrganizationDto addOrganizationMember(UUID organizationId, List<UUID> personIds, boolean primary) {
		if (!this.isUserAuthorizedForFieldEdit(Organization.MEMBERS_FIELD)) {
			throw new NotAuthorizedException(String.format(USER_NOT_AUTHORIZED_FIELD_EDIT_MSG, Organization.MEMBERS_FIELD));
		}
		
		return this.addMember(organizationId, personIds, primary);
	}

	/**
	 * Removes one or more members from an org - this method is also used by child-types to remove members
	 *
	 * @param organizationId The UUID of the organization to perform the operation on
	 * @param personIds      The list of UUIDs of type Person to remove
	 * @return The updated OrganizationDTO object
	 * 
	 * @throws NotAuthorizedException throws if requesting user does not have privilege to edit field
	 */
	@Override
	@Transactional(dontRollbackOn={IllegalOrganizationModification.class})
	public OrganizationDto removeOrganizationMember(UUID organizationId, List<UUID> personIds) {
		if (!this.isUserAuthorizedForFieldEdit(Organization.MEMBERS_FIELD)) {
			throw new NotAuthorizedException(String.format(USER_NOT_AUTHORIZED_FIELD_EDIT_MSG, Organization.MEMBERS_FIELD));
		}
		
		return this.removeMember(organizationId, personIds);
	}

	/**
	 * Adds one or more subordinate organizations from provided organization
	 *
	 * @param organizationId The UUID of the organization to perform the operation on
	 * @param orgIds         The list of UUIDs of type Organization to add as subordinates
	 * @return The updated OrganizationDTO object
	 * 
	 * @throws NotAuthorizedException throws if requesting user does not have privilege to edit field
	 */
	@Override
	@Transactional(dontRollbackOn={IllegalOrganizationModification.class})
	public OrganizationDto addSubordinateOrg(UUID organizationId, List<UUID> orgIds) {
		if (!this.isUserAuthorizedForFieldEdit(Organization.SUB_ORGS_FIELD)) {
			throw new NotAuthorizedException(String.format(USER_NOT_AUTHORIZED_FIELD_EDIT_MSG, Organization.SUB_ORGS_FIELD));
		}
		
		return this.addOrg(organizationId, orgIds);
	}

	/**
	 * Removes one or more subordinate organizations from provided organization
	 *
	 * @param organizationId The UUID of the organization to perform the operation on
	 * @param orgIds         The list of UUIDs of type Organization to removes from subordinates
	 * @return The updated OrganizationDTO object
	 * 
	 * @throws NotAuthorizedException throws if requesting user does not have privilege to edit field
	 */
	@Override
	@Transactional(dontRollbackOn={IllegalOrganizationModification.class})
	public OrganizationDto removeSubordinateOrg(UUID organizationId, List<UUID> orgIds) {
		if (!this.isUserAuthorizedForFieldEdit(Organization.SUB_ORGS_FIELD)) {
			throw new NotAuthorizedException(String.format(USER_NOT_AUTHORIZED_FIELD_EDIT_MSG, Organization.SUB_ORGS_FIELD));
		}
		
		return this.removeOrg(organizationId, orgIds);
	}

	/**
	 * Adds a list of Org DTOs as new entities in the database. If any creates fail,
	 * the entire operation is rolled back.
	 *
	 * Only fires one pub-sub event
	 * containing the UUIDs of the newly created Organizations.
	 *
	 * @param newOrgs List of Organization DTOs to add
	 * @return Same list of input Org DTOs (if they were all successfully created)
	 */
	@Transactional
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
	 * Private helper to make sure prior to setting an orgs parent, that the proposed parent
	 * is not already in the descendents of said organization.
	 * 
	 * @param org the org we're modifying the parent for
	 * @param parentUUIDCandidate the UUID of the proposed parent
	 */
	private void setOrgParentConditionally(Organization org, String parentUUIDCandidate) {

		// if we're setting parent to just null, update the parent (it doesn't have 'org' as a suborg anymore),
		// and skip all other checks below
		if (parentUUIDCandidate == null) {
			org.setParentOrganization(null);

			// we also need to modify the (former) parent to not have this
			//  as a sub org anymore, so go through its subordinate orgs and remove itself as the parent of org
			List<Organization> parentOrgs = repository.findOrganizationsBySubordinateOrganizationsContaining(org);
			for (Organization parent : parentOrgs) {
				parent.removeSubordinateOrganization(org);
				repository.saveAndFlush(parent);
			}

			return;
		}

		// not setting parent to null, so check down the family tree to see if parent
		//  candidate is a descendent already
		if (!this.parentOrgCandidateIsDescendent(convertToDto(org), UUID.fromString(parentUUIDCandidate))) {

			Organization parentOrg = repository.findById(UUID.fromString(parentUUIDCandidate)).orElseThrow(
					() -> new InvalidRecordUpdateRequest("Provided org UUID " + parentUUIDCandidate + " was not found"));

			// free this org from any former parent linkage
			List<Organization> parentOrgs = repository.findOrganizationsBySubordinateOrganizationsContaining(org);
			for (Organization parent : parentOrgs) {
				parent.removeSubordinateOrganization(org);
				repository.saveAndFlush(parent);
			}

			org.setParentOrganization(parentOrg);  // make sure we got the new parent locked in
			parentOrg.addSubordinateOrganization(org);  // update new parent's sub orgs field to include this org
			repository.save(parentOrg);  // now save the new parent org
			repository.save(org); // finally save sub org that was modified
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
		catch (NullPointerException e) {
			// apparently JSONPatch lib throws a NullPointerException on a null path, instead of a nicer one
			throw new InvalidRecordUpdateRequest("Json Patch cannot have a null path value");
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
	 * 
	 * Will not perform EFA on Organizations updated through this method.
	 * 
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

	/**
	 * @throws NotAuthorizedException throws if requesting user does not have privilege to edit field
	 */
	@Override
	@Transactional(dontRollbackOn={IllegalOrganizationModification.class})
	public OrganizationDto removeParentOrganization(UUID organizationId) {
		if (!this.isUserAuthorizedForFieldEdit(Organization.PARENT_ORG_FIELD)) {
			throw new NotAuthorizedException(String.format(USER_NOT_AUTHORIZED_FIELD_EDIT_MSG, Organization.PARENT_ORG_FIELD));
		}
		
		Map<String, String> noParentMap = new HashMap<>();
		noParentMap.put("parentOrganization", null);
		
		return this.modify(organizationId, noParentMap);
	}

	/**
	 * @throws NotAuthorizedException throws if requesting user does not have privilege to edit field
	 */
	@Override
	@Transactional(dontRollbackOn={IllegalOrganizationModification.class})
	public OrganizationDto removeLeader(UUID organizationId) {
		if (!this.isUserAuthorizedForFieldEdit(Organization.LEADER_FIELD)) {
			throw new NotAuthorizedException(String.format(USER_NOT_AUTHORIZED_FIELD_EDIT_MSG, Organization.LEADER_FIELD));
		}
		
		Map<String, String> noLeaderMap = new HashMap<>();
		noLeaderMap.put("leader", null);
		
		return this.modify(organizationId, noLeaderMap);
	}
	
	private OrganizationDto checkEfaResponseForIllegalModification(EntityFieldAuthResponse<Organization> efaResponse) {
		if (efaResponse.getDeniedFields().isEmpty()) {
			return this.convertToDto(efaResponse.getModifiedEntity());
		}
		
		throw new IllegalOrganizationModification(efaResponse);
	}
	
	private OrganizationDto checkEfaResponseForIllegalModification(Organization entity, List<String> deniedFields) {
		return checkEfaResponseForIllegalModification(
				EntityFieldAuthResponse.<Organization>builder()
					.deniedFields(deniedFields)
					.modifiedEntity(entity)
					.build());
	}
}
