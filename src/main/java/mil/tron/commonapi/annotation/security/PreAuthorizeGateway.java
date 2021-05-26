package mil.tron.commonapi.annotation.security;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.springframework.security.access.prepost.PreAuthorize;

@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@accessCheck.check(#requestObject) || hasAuthority('DASHBOARD_ADMIN')")
public @interface PreAuthorizeGateway {
    
}
