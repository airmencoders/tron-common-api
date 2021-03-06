package mil.tron.commonapi.service.documentspace;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import mil.tron.commonapi.dto.appclient.AppClientSummaryDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceAppClientMemberRequestDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceAppClientResponseDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpacePrivilegeDto;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.documentspace.DocumentSpace;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceDashboardMemberPrivilegeRow;
import mil.tron.commonapi.entity.documentspace.DocumentSpacePrivilege;

public interface DocumentSpacePrivilegeService {
	void deleteAllPrivilegesBelongingToDocumentSpace(DocumentSpace documentSpace);
	void createAndSavePrivilegesForNewSpace(DocumentSpace documentSpace);
	String createPrivilegeName(UUID documentSpaceId, DocumentSpacePrivilegeType privilegeType);
	void addPrivilegesToDashboardUser(DashboardUser dashboardUser, DocumentSpace documentSpace, List<DocumentSpacePrivilegeType> privilegesToAdd) throws IllegalArgumentException;
	void removePrivilegesFromDashboardUser(DashboardUser dashboardUser, DocumentSpace documentSpace);
	DashboardUser createDashboardUserWithPrivileges(String dashboardUserEmail, DocumentSpace documentSpace, List<DocumentSpacePrivilegeType> privilegesToAdd);
	List<DocumentSpaceDashboardMemberPrivilegeRow> getAllDashboardMemberPrivilegeRowsForDocumentSpace(DocumentSpace documentSpace, Set<UUID> dashboardUserIdsToInclude);
	DocumentSpacePrivilegeDto convertToDto(DocumentSpacePrivilege documentSpacePrivilege);

	// app client privilege methods
	void addPrivilegesToAppClientUser(DocumentSpace documentSpace, UUID appClientId, List<DocumentSpacePrivilegeType> privilegesToAdd);
	void removePrivilegesFromAppClientUser(DocumentSpace documentSpace, UUID appClientId);
	List<DocumentSpaceAppClientResponseDto> getAppClientsForDocumentSpace(DocumentSpace documentSpace);
	List<AppClientSummaryDto> getAppClientsForAssignmentToDocumentSpace(DocumentSpace documentSpace);
}
