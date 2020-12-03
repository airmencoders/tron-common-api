package mil.tron.commonapi.service;

import java.util.Collection;
import java.util.UUID;

import mil.tron.commonapi.organization.Organization;

public interface OrganizationService {
	public abstract Organization createOrganization(Organization organization);
	public abstract Organization updateOrganization(UUID id, Organization organization);
	public abstract void deleteOrganization(UUID id);
	public abstract Collection<Organization> getOrganizations();
	public abstract Organization getOrganization(UUID id);
}
