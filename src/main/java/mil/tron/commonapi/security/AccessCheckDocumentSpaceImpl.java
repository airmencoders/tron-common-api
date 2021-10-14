package mil.tron.commonapi.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import mil.tron.commonapi.service.documentspace.DocumentSpacePrivilegeService;
import mil.tron.commonapi.service.documentspace.DocumentSpacePrivilegeType;
import mil.tron.commonapi.service.documentspace.DocumentSpaceServiceImpl;

public class AccessCheckDocumentSpaceImpl implements AccessCheckDocumentSpace {
	private final DocumentSpacePrivilegeService documentSpacePrivilegeService;
	
	public AccessCheckDocumentSpaceImpl(DocumentSpacePrivilegeService documentSpacePrivilegeService) {
		this.documentSpacePrivilegeService = documentSpacePrivilegeService;
	}

	@Override
	public boolean hasWriteAccess(Authentication authentication, UUID documentSpaceId) {
		if (!isValidAccessCheckRequest(authentication, documentSpaceId)) {
			return false;
		}

		return authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals(
				documentSpacePrivilegeService.createPrivilegeName(documentSpaceId, DocumentSpacePrivilegeType.WRITE)) || isDashboardAdmin(authority));
	}

	@Override
	public boolean hasReadAccess(Authentication authentication, UUID documentSpaceId) {
		if (!isValidAccessCheckRequest(authentication, documentSpaceId)) {
			return false;
		}
		
		return authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals(
				documentSpacePrivilegeService.createPrivilegeName(documentSpaceId, DocumentSpacePrivilegeType.READ)) || isDashboardAdmin(authority));
	}

	@Override
	public boolean hasMembershipAccess(Authentication authentication, UUID documentSpaceId) {
		if (!isValidAccessCheckRequest(authentication, documentSpaceId)) {
			return false;
		}

		return authentication.getAuthorities().stream()
				.anyMatch(authority -> authority.getAuthority().equals(documentSpacePrivilegeService
						.createPrivilegeName(documentSpaceId, DocumentSpacePrivilegeType.MEMBERSHIP)) || isDashboardAdmin(authority));
	}
	
	@Override
	public boolean hasDocumentSpaceAccess(Authentication authentication) {
		if (authentication == null) {
			return false;
		}
		
		return authentication.getAuthorities().stream().anyMatch(authority -> 
			 authority.getAuthority().equalsIgnoreCase(DocumentSpaceServiceImpl.DOCUMENT_SPACE_USER_PRIVILEGE) || isDashboardAdmin(authority));
	}
	
	private boolean isValidAccessCheckRequest(Authentication authentication, UUID documentSpaceId) {
		return authentication != null && documentSpaceId != null;
	}
	
	private boolean isDashboardAdmin(GrantedAuthority authority) {
		return authority.getAuthority().equalsIgnoreCase("DASHBOARD_ADMIN");
	}

}
