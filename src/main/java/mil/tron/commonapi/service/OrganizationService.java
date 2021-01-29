package mil.tron.commonapi.service;

import mil.tron.commonapi.dto.OrganizationDto;
import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.orgtypes.Unit;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface OrganizationService {

	// entity methods (service <--> persistence)
	Organization findOrganization(UUID id);
	Iterable<Organization> findOrganizationsByTypeAndService(Unit type, Branch branch);
	Organization removeMember(UUID organizationId, List<UUID> personIds);
	Organization addMember(UUID organizationId, List<UUID> personIds);
	Organization modify(UUID organizationId, Map<String, String> attribs);
	Organization addOrg(UUID organizationId, List<UUID> orgIds);
	Organization removeOrg(UUID organizationId, List<UUID> orgIds);

	// methods dealing only with DTO (service <--> controller)
	OrganizationDto createOrganization(OrganizationDto organization);
	OrganizationDto updateOrganization(UUID id, OrganizationDto organization);
	void deleteOrganization(UUID id);
	Iterable<OrganizationDto> getOrganizations();
	Iterable<OrganizationDto> getOrganizationsByTypeAndService(Unit type, Branch branch);
	OrganizationDto getOrganization(UUID id);
	OrganizationDto modifyAttributes(UUID organizationId, Map<String, String> attribs);
	OrganizationDto removeOrganizationMember(UUID organizationId, List<UUID> personIds);
	OrganizationDto addOrganizationMember(UUID organizationId, List<UUID> personIds);
	OrganizationDto addSubordinateOrg(UUID organizationId, List<UUID> orgIds);
	OrganizationDto removeSubordinateOrg(UUID organizationId, List<UUID> orgIds);
	List<OrganizationDto> bulkAddOrgs(List<OrganizationDto> newOrgs);

	// conversion methods
	OrganizationDto convertToDto(Organization org);
	Organization convertToEntity(OrganizationDto org);
}
