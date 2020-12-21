package mil.tron.commonapi.service;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.PersonRepository;
import org.springframework.stereotype.Service;

import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.repository.OrganizationRepository;
import org.springframework.util.ReflectionUtils;

@Service
public class OrganizationServiceImpl implements OrganizationService {
	private final OrganizationRepository repository;
	private final PersonRepository personRepository;
	
	public OrganizationServiceImpl(OrganizationRepository repository, PersonRepository personRepository) {
		this.repository = repository;
		this.personRepository = personRepository;
	}
		
	@Override
	public Organization createOrganization(Organization organization) {
		return repository.existsById(organization.getId()) ? null : repository.save(organization);
	}

	@Override
	public Organization updateOrganization(UUID id, Organization organization) {
		if (!id.equals(organization.getId()) || !repository.existsById(id))
			return null;
		
		return repository.save(organization);
	}

	@Override
	public void deleteOrganization(UUID id) {
		repository.deleteById(id);
	}

	@Override
	public Iterable<Organization> getOrganizations() {
		return repository.findAll();
	}

	@Override
	public Organization getOrganization(UUID id) {
		return repository.findById(id).orElse(null);
	}

	@Override
	public Organization addOrganizationMember(UUID organizationId, List<UUID> personIds) {
		Organization organization = repository.findById(organizationId).orElseThrow(
				() -> new RecordNotFoundException("Provided organization UUID " + organizationId.toString() + " not match any existing records"));

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
				() -> new RecordNotFoundException("Provided organization UUID " + organizationId.toString() + "  does not match any existing records"));

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
				() -> new RecordNotFoundException("Provided org UUID " + organizationId.toString() + " does not match any existing records"));

		attribs.forEach((k, v) -> {
			Field field = ReflectionUtils.findField(Organization.class, k);
			if (field != null) {
				field.setAccessible(true);
				if (v == null) {
					ReflectionUtils.setField(field, organization, null);
				} else if (field.getType().equals(Person.class)) {
					Person person = personRepository.findById(UUID.fromString(v))
							.orElseThrow(() -> new InvalidRecordUpdateRequest("Provided person UUID " + v + " does not match any existing records"));
					ReflectionUtils.setField(field, organization, person);
				} else if (field.getType().equals(Organization.class)) {
					Organization org = repository.findById(UUID.fromString(v)).orElseThrow(
							() -> new InvalidRecordUpdateRequest("Provided org UUID " + v + " does not match any existing records"));
					ReflectionUtils.setField(field, organization, org);
				} else {
					ReflectionUtils.setField(field, organization, v);
				}
			}
		});

		return repository.save(organization);
	}

}
