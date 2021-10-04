package mil.tron.commonapi.service.documentspace;

import java.util.List;
import java.util.UUID;

import mil.tron.commonapi.dto.documentspace.DocumentSpacePrivilegeDto;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.documentspace.DocumentSpace;
import mil.tron.commonapi.entity.documentspace.DocumentSpacePrivilege;

public interface DocumentSpacePrivilegeService {
	void deleteAllPrivilegesBelongingToDocumentSpace(DocumentSpace documentSpace);
	List<DocumentSpacePrivilege> createPrivilegesForNewSpace(UUID documentSpaceId);
	
	String createPrivilegeName(UUID documentSpaceId, DocumentSpacePrivilegeType privilegeType);
	
	void addPrivilegesToDashboardUser(DashboardUser dashboardUser, DocumentSpace documentSpace, List<DocumentSpacePrivilegeType> privilegesToAdd) throws IllegalArgumentException;
	void removePrivilegesFromDashboardUser(String dashboardUserEmail, DocumentSpace documentSpace, List<DocumentSpacePrivilegeType> privilegesToRemove);
	DashboardUser createDashboardUserWithPrivileges(String dashboardUserEmail, DocumentSpace documentSpace, List<DocumentSpacePrivilegeType> privilegesToAdd);
	
	DocumentSpacePrivilegeDto convertToDto(DocumentSpacePrivilege documentSpacePrivilege);
}
