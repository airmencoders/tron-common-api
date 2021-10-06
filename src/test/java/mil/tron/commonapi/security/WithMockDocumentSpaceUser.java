package mil.tron.commonapi.security;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.springframework.security.test.context.support.WithSecurityContext;

import mil.tron.commonapi.service.documentspace.DocumentSpacePrivilegeType;

@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockDocumentSpaceUserSecurityContextFactory.class)
public @interface WithMockDocumentSpaceUser {
	String username() default "test@email.com";
	DocumentSpacePrivilegeType[] withPrivileges();
	String documentSpaceId();
}
