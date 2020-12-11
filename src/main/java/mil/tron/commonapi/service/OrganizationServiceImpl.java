package mil.tron.commonapi.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import mil.tron.commonapi.organization.Organization;
import mil.tron.commonapi.repository.OrganizationRepository;

@Service
public class OrganizationServiceImpl implements OrganizationService {
	private OrganizationRepository repository;
	
	public OrganizationServiceImpl(OrganizationRepository repository) {
		this.repository = repository;
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

}
