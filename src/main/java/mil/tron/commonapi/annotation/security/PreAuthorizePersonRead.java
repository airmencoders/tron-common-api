package mil.tron.commonapi.annotation.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAuthority('PERSON_READ') || hasAuthority('DASHBOARD_ADMIN')")
public @interface PreAuthorizePersonRead {
}
