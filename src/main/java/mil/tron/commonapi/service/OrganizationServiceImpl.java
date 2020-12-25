package mil.tron.commonapi.service;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.PersonRepository;
import org.springframework.stereotype.Service;

import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.repository.OrganizationRepository;
import org.springframework.util.ReflectionUtils;

@Service
public class OrganizationServiceImpl implements OrganizationService {
	private final OrganizationRepository repository;
	private final PersonRepository personRepository;

	private final String errorMsg = "Provided organization UUID %s does not match any existing records";
	private static final String RESOURCE_NOT_FOUND_MSG = "Resource with the ID: %s does not exist.";
	
	public OrganizationServiceImpl(OrganizationRepository repository, PersonRepository personRepository) {
		this.repository = repository;
		this.personRepository = personRepository;
	}
		
	@Override
	public Organization createOrganization(Organization organization) {
		if (repository.existsById(organization.getId()))
			throw new ResourceAlreadyExistsException(String.format("Resource with the ID: %s already exists.", organization.getId()));
		
		/**
		 * Unique Name Constraint
		 */
		if (organization.getName() != null && repository.findByNameIgnoreCase(organization.getName()).isPresent())
			throw new ResourceAlreadyExistsException(String.format("Resource with the Name: %s already exists.", organization.getName()));
		
		return repository.save(organization);
	}

	@Override
	public Organization updateOrganization(UUID id, Organization organization) {
		if (!id.equals(organization.getId()))
			throw new InvalidRecordUpdateRequest(String.format("ID: %s does not match the resource ID: %s", id, organization.getId()));
		
		Optional<Organization> dbOrg = repository.findById(id);
		
		if (dbOrg.isEmpty())
			throw new RecordNotFoundException(String.format(RESOURCE_NOT_FOUND_MSG, id));
		
		/**
		 * Unique Name Check
		 * 
		 * First check if there is a name change update.
		 * If there is a name change, look in the database
		 * for any organization that is using the new name.
		 * Throw exception if an organization with the new name
		 * already exists.
		 */
		String orgName = organization.getName();
		if (orgName != null && !orgName.equalsIgnoreCase(dbOrg.get().getName()) && repository.findByNameIgnoreCase(orgName).isPresent())
			throw new InvalidRecordUpdateRequest(String.format("Name: %s is already in use.", orgName));
		
		return repository.save(organization);
	}

	@Override
	public void deleteOrganization(UUID id) {
		if (repository.existsById(id))
			repository.deleteById(id);
		else
			throw new RecordNotFoundException(String.format(RESOURCE_NOT_FOUND_MSG, id));
	}

	@Override
	public Iterable<Organization> getOrganizations() {
		return repository.findAll();
	}

	@Override
	public Organization getOrganization(UUID id) {
		return repository.findById(id).orElseThrow(() -> new RecordNotFoundException(String.format(RESOURCE_NOT_FOUND_MSG, id)));
	}

	@Override
	public Organization addOrganizationMember(UUID organizationId, List<UUID> personIds) {
		Organization organization = repository.findById(organizationId).orElseThrow(
				() -> new RecordNotFoundException(String.format(errorMsg, organizationId.toString())));

		for (UUID id : personIds) {
			Person person = personRepository.findById(id).orElseThrow(
					() -> new InvalidRecordUpdateRequest("Provided person UUID " + id.toString() + " does not exist"));

			organization.addMember(person);
		}


		return repository.save(organization);
	}

	@Override
	public Organization removeOrganizationMember(UUID organizationId, List<UUID> personIds) {
		Organization organization = repository.findById(organizationId).orElseThrow(
				() -> new RecordNotFoundException(String.format(errorMsg, organizationId.toString())));

		for (UUID id : personIds) {
			Person person = personRepository.findById(id).orElseThrow(
					() -> new InvalidRecordUpdateRequest("A provided person UUID " + id.toString() + " does not exist"));

			organization.removeMember(person);
		}

		return repository.save(organization);
	}

	/**
	 * Modifies an organization's attributes (except members) such as Leader, Parent Org, and Name
	 * @param organizationId UUID of the organization to modify
	 * @param attribs a HashMap of fields to change (in key/value form)
	 * @return the modified and persisted organization
	 */
	@Override
	public Organization modifyAttributes(UUID organizationId, Map<String, String> attribs) {
		Organization organization = repository.findById(organizationId).orElseThrow(
				() -> new RecordNotFoundException(String.format(errorMsg, organizationId.toString())));

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

}
