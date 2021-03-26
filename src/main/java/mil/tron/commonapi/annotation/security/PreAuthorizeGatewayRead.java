package mil.tron.commonapi.annotation.security;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.springframework.security.access.prepost.PreAuthorize;

@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("#requestObject.getRequestURI().split('/').length > 4 && hasAuthority(#requestObject.getRequestURI().split('/')[4] + 'READ')")
public @interface PreAuthorizeGatewayRead {
    
}
