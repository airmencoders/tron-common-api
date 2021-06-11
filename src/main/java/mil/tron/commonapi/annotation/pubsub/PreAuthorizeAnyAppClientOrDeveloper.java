package mil.tron.commonapi.annotation.pubsub;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Preauthorizes access to pub sub ledger/replay/event endpoints if requester is a:
 *  - APP_CLIENT_DEVELOPER of any app, or
 *  - any APP_CLIENT, or
 *  - a DASHBOARD_ADMIN
 */

@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAuthority('APP_CLIENT_DEVELOPER') || hasAuthority('APP_CLIENT') || hasAuthority('DASHBOARD_ADMIN')")
public @interface PreAuthorizeAnyAppClientOrDeveloper {
}
