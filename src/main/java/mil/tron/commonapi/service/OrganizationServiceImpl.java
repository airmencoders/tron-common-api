package mil.tron.commonapi.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.UUID;

import org.springframework.stereotype.Service;

import mil.tron.commonapi.organization.Organization;

@Service
public class OrganizationServiceImpl implements OrganizationService {
	
	private HashMap<UUID, Organization> organizations = new HashMap<>();

	@Override
	public Organization createOrganization(Organization organization) {
		if (organizations.put(UUID.randomUUID(), organization) != null)
			return organization;
		else
			return null;
	}

	@Override
	public Organization updateOrganization(UUID id, Organization organization) {
		if (organizations.replace(id, organization) != null)
			return organization;
		else
			return null;
	}

	@Override
	public void deleteOrganization(UUID id) {
		organizations.remove(id);
	}

	@Override
	public Collection<Organization> getOrganizations() {
		return organizations.values();
	}

	@Override
	public Organization getOrganization(UUID id) {
		return organizations.get(id);
	}

}
