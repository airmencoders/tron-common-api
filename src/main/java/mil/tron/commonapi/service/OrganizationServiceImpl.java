package mil.tron.commonapi.service;

import java.util.Map;
import java.util.UUID;

import mil.tron.commonapi.entity.Airman;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.Squadron;
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

	/**
	 * Modifies an organization's attributes (except members) such as Leader, Parent Org, and Name
	 * @param organizationId UUID of the organization to modify
	 * @param attribs a HashMap of fields to change (in key/value form)
	 * @return the modified and persisted organization
	 */
	@Override
	public Organization modifyAttributes(UUID organizationId, Map<String, String> attribs) {
		Organization organization = repository.findById(organizationId).orElseThrow(
				() -> new RecordNotFoundException("Provided org UUID does not match any existing records"));

		// change org's leader
		if (attribs.containsKey("leader")) {
			if (attribs.get("leader") == null) {
				organization.setLeader(null);
			} else {
				Person person = personRepository.findById(UUID.fromString(attribs.get("leader")))
						.orElseThrow(() -> new RecordNotFoundException("Provided leader UUID does not match any existing records"));

				organization.setLeader(person);
			}
		}

		// change org's name
		if (attribs.containsKey("name")) {
			if (attribs.get("name") == null) {
				organization.setName(null);
			} else {
				organization.setName(attribs.get("name"));
			}
		}

		// change parent organization
		if (attribs.containsKey("parentOrganization")) {
			if (attribs.get("parentOrganization") == null) {
				organization.setParentOrganization(null);
			} else {
				Organization parent = repository.findById(UUID.fromString(attribs.get("parentOrganization"))).orElseThrow(
						() -> new RecordNotFoundException("Provided org UUID does not match any existing records"));

				organization.setParentOrganization(parent);
			}
		}

		return repository.save(organization);
	}

}
