package mil.tron.commonapi.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;

import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.repository.AppClientUserRespository;

public class AccessCheckEventRequestLogImpl implements AccessCheckEventRequestLog {
	private final AppClientUserRespository appClientRepo;
	
	public AccessCheckEventRequestLogImpl(AppClientUserRespository appClientRepo) {
		this.appClientRepo = appClientRepo;
	}

	@Override
	public boolean isRequesterAppClientOrDeveloper(Authentication authentication, UUID appClientId) {
		if (authentication == null || appClientId == null) {
			return false;
		}
		
		Optional<AppClientUser> appClient = appClientRepo.findById(appClientId);
		
		if (appClient.isPresent()) {
			String authenticatedUsername = authentication.getName();
			
			return authenticatedUsername.equalsIgnoreCase(appClient.get().getName()) || 
					appClient.get().getAppClientDevelopers().stream().anyMatch(dashboardUser -> dashboardUser.getEmail().equalsIgnoreCase(authenticatedUsername));
		}
		
		return false;
	}

}
