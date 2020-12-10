package mil.tron.commonapi.service;

import java.util.UUID;

import mil.tron.commonapi.organization.Organization;

public interface OrganizationService {
	public abstract Organization createOrganization(Organization organization);
	public abstract Organization updateOrganization(UUID id, Organization organization);
	public abstract void deleteOrganization(UUID id);
	public abstract Iterable<Organization> getOrganizations();
	public abstract Organization getOrganization(UUID id);
}
