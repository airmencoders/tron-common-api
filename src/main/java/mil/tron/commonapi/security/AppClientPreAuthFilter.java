package mil.tron.commonapi.security;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

public class AppClientPreAuthFilter extends AbstractPreAuthenticatedProcessingFilter  {
	
	private static final String NAMESPACE_REGEX = "(?<=\\/ns\\/)([^\\/]*)";
	private static final Pattern NAMESPACE_PATTERN = Pattern.compile(NAMESPACE_REGEX);
	
	@Override
	protected Object getPreAuthenticatedPrincipal(HttpServletRequest request) {
		String header = request.getHeader("x-forwarded-client-cert");
		String uri = extractUriFromXfccHeader(header);

		return extractNamespaceFromUri(uri);
	}

	private DecodedJWT decodeJwt (HttpServletRequest request) {
		String bearer = request.getHeader("authorization");
		return JWT.decode(bearer.split("Bearer ")[1]);
	}

	@Override
	protected Object getPreAuthenticatedCredentials(HttpServletRequest request) {
		return "N/A";
	}
	
	/**
	 * Extracts URI field from an x-forwarded-client-cert header
	 * 
	 * @param header the full x-forwarded-client-cert header
	 * @return the URI string field of the header or null if header is malformed
	 */
	private String extractUriFromXfccHeader(String header) {
		if (header == null || header.isBlank()) 
			return null;
		
		Map<String, String> xfcc = new HashMap<>();
		String[] splitByDelimiter = header.split(";");
		
		/*
		 * In the event that a malformed xfcc header is sent in,
		 * just return null
		 */
		try {
			for (String item : splitByDelimiter) {
				String[] pairs = item.split("=");
				xfcc.put(pairs[0], pairs[1]);
			}
		} catch (Exception ex) {
			return null;
		}
		
		
		if (xfcc.containsKey("URI"))
			return xfcc.get("URI");
		else
			return null;
	}
	
	/**
	 * Extracts the namespace value from the URI field of an x-forwarded-client-cert header
	 * 
	 * @param uri the uri field extract from an x-forwarded-client-cert header
	 * @return the namespace value of the URI
	 */
	private String extractNamespaceFromUri(String uri) {
		if (uri == null || uri.isBlank()) 
			return null;
		
		Matcher extractNs = NAMESPACE_PATTERN.matcher(uri);
		if (extractNs.find()) 
			return extractNs.group();
		else
			return null;
	}
}
