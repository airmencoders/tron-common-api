package mil.tron.commonapi.service;

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

		// change org's leader
		final String LEADER = "leader";
		if (attribs.containsKey(LEADER)) {
			if (attribs.get(LEADER) == null) {
				organization.setLeader(null);
			} else {
				Person person = personRepository.findById(UUID.fromString(attribs.get(LEADER)))
						.orElseThrow(() -> new InvalidRecordUpdateRequest("Provided leader UUID " + attribs.get(LEADER) + " does not match any existing records"));

				organization.setLeader(person);
			}
		}

		// change org's name
		final String ORG_NAME = "name";
		if (attribs.containsKey(ORG_NAME)) {
			if (attribs.get(ORG_NAME) == null) {
				organization.setName(null);
			} else {
				organization.setName(attribs.get(ORG_NAME));
			}
		}

		// change parent organization
		final String PARENT_ORG = "parentOrganization";
		if (attribs.containsKey(PARENT_ORG)) {
			if (attribs.get(PARENT_ORG) == null) {
				organization.setParentOrganization(null);
			} else {
				Organization parent = repository.findById(UUID.fromString(attribs.get(PARENT_ORG))).orElseThrow(
						() -> new InvalidRecordUpdateRequest("Provided org UUID " + attribs.get(PARENT_ORG) + " does not match any existing records"));

				organization.setParentOrganization(parent);
			}
		}

		return repository.save(organization);
	}

}
