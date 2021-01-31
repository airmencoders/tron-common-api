package mil.tron.commonapi.annotation.security;

import org.springframework.security.access.prepost.PreAuthorize;

@PreAuthorize("hasAuthority('READ')")
public @interface PreAuthorizeRead {

}
