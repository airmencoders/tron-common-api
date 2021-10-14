package mil.tron.commonapi.security;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.assertj.core.util.Arrays;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import mil.tron.commonapi.service.documentspace.DocumentSpaceServiceImpl;


public class WithMockDocumentSpaceUserSecurityContextFactory implements WithSecurityContextFactory<WithMockDocumentSpaceUser> {
	@Override
	public SecurityContext createSecurityContext(WithMockDocumentSpaceUser customUser) {
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		
		List<SimpleGrantedAuthority> authorities = new ArrayList<>();
		
		if (customUser.withPrivileges() != null && customUser.withPrivileges().length > 0) {
			authorities.addAll(Arrays.asList(customUser.withPrivileges()).stream()
					.map(documentSpacePrivilege -> new SimpleGrantedAuthority(String.format("DOCUMENT_SPACE_%s_%s",
							customUser.documentSpaceId(), documentSpacePrivilege.toString())))
					.collect(Collectors.toList()));
			
			authorities.add(new SimpleGrantedAuthority(DocumentSpaceServiceImpl.DOCUMENT_SPACE_USER_PRIVILEGE));
		}
		
		User principal = new User(customUser.username(), "password", authorities);
		Authentication auth = new UsernamePasswordAuthenticationToken(principal, "password", authorities);
		context.setAuthentication(auth);
		return context;
	}
}