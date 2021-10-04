package mil.tron.commonapi.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;

import mil.tron.commonapi.service.documentspace.DocumentSpacePrivilegeService;
import mil.tron.commonapi.service.documentspace.DocumentSpacePrivilegeType;

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
				documentSpacePrivilegeService.createPrivilegeName(documentSpaceId, DocumentSpacePrivilegeType.WRITE)));
	}

	@Override
	public boolean hasReadAccess(Authentication authentication, UUID documentSpaceId) {
		if (!isValidAccessCheckRequest(authentication, documentSpaceId)) {
			return false;
		}

		return authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals(
				documentSpacePrivilegeService.createPrivilegeName(documentSpaceId, DocumentSpacePrivilegeType.READ)));
	}

	@Override
	public boolean hasMembershipAccess(Authentication authentication, UUID documentSpaceId) {
		if (!isValidAccessCheckRequest(authentication, documentSpaceId)) {
			return false;
		}

		return authentication.getAuthorities().stream()
				.anyMatch(authority -> authority.getAuthority().equals(documentSpacePrivilegeService
						.createPrivilegeName(documentSpaceId, DocumentSpacePrivilegeType.MEMBERSHIP)));
	}
	
	private boolean isValidAccessCheckRequest(Authentication authentication, UUID documentSpaceId) {
		return authentication != null && documentSpaceId != null;
	}

}
