package mil.tron.commonapi.service;

import java.util.UUID;

import mil.tron.commonapi.organization.Organization;

public interface OrganizationService {
	Organization createOrganization(Organization organization);
	Organization updateOrganization(UUID id, Organization organization);
	void deleteOrganization(UUID id);
	Iterable<Organization> getOrganizations();
	Organization getOrganization(UUID id);
}
