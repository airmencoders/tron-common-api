package mil.tron.commonapi.security;


import java.util.UUID;

import org.springframework.security.core.Authentication;

public interface AccessCheckDocumentSpace {
	boolean hasWriteAccess(Authentication authentication, UUID documentSpaceId);
	boolean hasReadAccess(Authentication authentication, UUID documentSpaceId);
	boolean hasMembershipAccess(Authentication authentication, UUID documentSpaceId);
}
