package mil.tron.commonapi.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import mil.tron.commonapi.entity.Organization;

public interface OrganizationService {
	Organization createOrganization(Organization organization);
	Organization updateOrganization(UUID id, Organization organization);
	void deleteOrganization(UUID id);
	Iterable<Organization> getOrganizations();
	Organization getOrganization(UUID id);

	Organization modifyAttributes(UUID organizationId, Map<String, String> attribs);
	Organization removeOrganizationMember(UUID organizationId, List<UUID> personIds);
	Organization addOrganizationMember(UUID organizationId, List<UUID> personIds);

	List<Organization> bulkAddOrgs(List<Organization> newOrgs);
}
