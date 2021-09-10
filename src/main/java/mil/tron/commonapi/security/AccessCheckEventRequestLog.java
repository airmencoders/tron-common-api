package mil.tron.commonapi.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;

public interface AccessCheckEventRequestLog {
	/**
	 * Checks to ensure the requester is authorized to read log entries for the specified App Client.
	 * Either the requester must be the App Client itself, or the requestor must be a developer
	 * of the App Client.
	 * @param appClientId the ID of the App Client to get log entries for
	 * @return true if authoried, false otherwise
	 */
	boolean isRequesterAppClientOrDeveloper(Authentication authentication, UUID appClientId);
}
