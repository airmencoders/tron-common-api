package mil.tron.commonapi.annotation.security;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.springframework.security.access.prepost.PreAuthorize;

@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAuthority('READ') || hasAuthority('DASHBOARD_USER') || hasAuthority('DASHBOARD_ADMIN')")
public @interface PreAuthorizeRead {

}
