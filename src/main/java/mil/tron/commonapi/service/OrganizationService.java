package mil.tron.commonapi.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import mil.tron.commonapi.dto.OrganizationDto;
import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.entity.orgtypes.Unit;

public interface OrganizationService {
	Organization findOrganization(UUID id);
	Iterable<Organization> findOrganizationsByType(Unit type);
	Organization removeMember(UUID organizationId, List<UUID> personIds);
	Organization addMember(UUID organizationId, List<UUID> personIds);
	Organization modify(UUID organizationId, Map<String, String> attribs);
	Organization addOrg(UUID organizationId, List<UUID> orgIds);
	Organization removeOrg(UUID organizationId, List<UUID> orgIds);

	// methods dealing only with DTO
	OrganizationDto createOrganization(OrganizationDto organization);
	OrganizationDto updateOrganization(UUID id, OrganizationDto organization);
	void deleteOrganization(UUID id);
	Iterable<OrganizationDto> getOrganizations();
	Iterable<OrganizationDto> getOrganizationsByType(Unit type);
	OrganizationDto getOrganization(UUID id);

	OrganizationDto modifyAttributes(UUID organizationId, Map<String, String> attribs);
	OrganizationDto removeOrganizationMember(UUID organizationId, List<UUID> personIds);
	OrganizationDto addOrganizationMember(UUID organizationId, List<UUID> personIds);

	OrganizationDto addSubordinateOrg(UUID organizationId, List<UUID> orgIds);
	OrganizationDto removeSubordinateOrg(UUID organizationId, List<UUID> orgIds);

	List<OrganizationDto> bulkAddOrgs(List<OrganizationDto> newOrgs);

	OrganizationDto convertToDto(Organization org);
	Organization convertToEntity(OrganizationDto org);
}
