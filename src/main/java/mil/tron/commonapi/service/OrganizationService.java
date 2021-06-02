package mil.tron.commonapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonpatch.JsonPatch;
import mil.tron.commonapi.dto.OrganizationDto;
import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.orgtypes.Unit;
import mil.tron.commonapi.repository.filter.FilterCriteria;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface OrganizationService {

	// entity methods (service <--> persistence)
	Organization findOrganization(UUID id);
	Iterable<Organization> findOrganizationsByTypeAndService(String searchQuery, Unit type, Branch branch, Pageable page);
	Organization removeMember(UUID organizationId, List<UUID> personIds);
	Organization addMember(UUID organizationId, List<UUID> personIds, boolean primary);
	Organization addOrg(UUID organizationId, List<UUID> orgIds);
	Organization removeOrg(UUID organizationId, List<UUID> orgIds);
	void removeLeaderByUuid(UUID leaderUuid);

	// methods dealing only with DTO (service <--> controller)
	OrganizationDto createOrganization(OrganizationDto organization);
	OrganizationDto updateOrganization(UUID id, OrganizationDto organization);
	OrganizationDto modify(UUID organizationId, Map<String, String> attribs);
	OrganizationDto patchOrganization(UUID id, JsonPatch patch);
	void deleteOrganization(UUID id);
	Iterable<OrganizationDto> getOrganizations(String searchQuery, Pageable page);
	Iterable<OrganizationDto> getOrganizationsByTypeAndService(String searchQuery, Unit type, Branch branch, Pageable page);
	OrganizationDto getOrganization(UUID id);
	OrganizationDto removeOrganizationMember(UUID organizationId, List<UUID> personIds);
	OrganizationDto addOrganizationMember(UUID organizationId, List<UUID> personIds, boolean primary);
	OrganizationDto addSubordinateOrg(UUID organizationId, List<UUID> orgIds);
	OrganizationDto removeSubordinateOrg(UUID organizationId, List<UUID> orgIds);
	List<OrganizationDto> bulkAddOrgs(List<OrganizationDto> newOrgs);

	// utility methods dealing with ancestry
	boolean orgIsInAncestryChain(UUID id, Organization startingOrg);
	boolean parentOrgCandidateIsDescendent(OrganizationDto org, UUID candidateParentId);
	OrganizationDto flattenOrg(OrganizationDto org);

	// conversion methods
	OrganizationDto convertToDto(Organization org);
	Organization convertToEntity(OrganizationDto org);
	JsonNode customizeEntity(Map<String, String> fields, OrganizationDto dto);

	OrganizationDto applyPatchToOrganization(JsonPatch patch, OrganizationDto organizationDto);
	
	Page<OrganizationDto> getOrganizationsPage(String searchQuery, Pageable page);
	Slice<OrganizationDto> getOrganizationsSlice(String searchQuery, Pageable page);
	
	Page<Organization> findOrganizationsByTypeAndServicePage(String searchQuery, Unit type, Branch branch, Pageable page);
	Page<OrganizationDto> getOrganizationsByTypeAndServicePage(String searchQuery, Unit type, Branch branch, Pageable page);
	
	Slice<Organization> findOrganizationsByTypeAndServiceSlice(String searchQuery, Unit type, Branch branch, Pageable page);
	Slice<OrganizationDto> getOrganizationsByTypeAndServiceSlice(String searchQuery, Unit type, Branch branch, Pageable page);
	
	Page<OrganizationDto> getOrganizationsPageSpec(List<FilterCriteria> filterCriteria, Pageable page);
}
