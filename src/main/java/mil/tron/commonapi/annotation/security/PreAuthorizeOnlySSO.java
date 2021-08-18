package mil.tron.commonapi.annotation.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Forbids App Clients - only SSO requests (aka requests from the web or dashboard)
 */
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("!hasAuthority('APP_CLIENT')")
public @interface PreAuthorizeOnlySSO {
}
