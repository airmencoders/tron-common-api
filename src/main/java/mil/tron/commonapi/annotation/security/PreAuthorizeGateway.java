package mil.tron.commonapi.annotation.security;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.springframework.security.access.prepost.PreAuthorize;

@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@accessCheck.check(#requestObject)")
public @interface PreAuthorizeGateway {
    
}
