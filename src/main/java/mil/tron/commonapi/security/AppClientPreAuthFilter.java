package mil.tron.commonapi.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppClientPreAuthFilter extends AbstractPreAuthenticatedProcessingFilter  {

	public static final String XFCC_HEADER_NAME = "x-forwarded-client-cert";
	private static final String NAMESPACE_REGEX = "(?<=\\/ns\\/)([^\\/]*)";
	private static final Pattern NAMESPACE_PATTERN = Pattern.compile(NAMESPACE_REGEX);
	
	@Override
	protected Object getPreAuthenticatedPrincipal(HttpServletRequest request) {
		String header = request.getHeader(XFCC_HEADER_NAME);
		String uri = extractUriFromXfccHeader(header);
		
		return extractNamespaceFromUri(uri);
	}

	/**
	 * If request has a JWT and has an email field, stash it in the credentials for now
	 * @param request injected HTTP Request
	 * @return either "N/A" or the email contained in the JWT
	 */
	@Override
	protected Object getPreAuthenticatedCredentials(HttpServletRequest request) {
		String authHeader = request.getHeader("authorization");
		if (authHeader != null) {
			String[] jwtParts = authHeader.split("Bearer ");
			if (jwtParts.length > 1) {
				try {
					DecodedJWT jwt = JWT.decode(jwtParts[1]);
					if (jwt.getClaim("email") != null) {
						return jwt.getClaim("email").asString();
					}
				}
				catch (JWTDecodeException ignored) {
					return null;
				}
			}
		}
		return "N/A";
	}
	
	/**
	 * Extracts URI field from an x-forwarded-client-cert header
	 * 
	 * @param header the full x-forwarded-client-cert header
	 * @return the URI string field of the header or null if header is malformed
	 */
	public static String extractUriFromXfccHeader(String header) {
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
	public static String extractNamespaceFromUri(String uri) {
		if (uri == null || uri.isBlank()) 
			return null;
		
		Matcher extractNs = NAMESPACE_PATTERN.matcher(uri);
		if (extractNs.find()) 
			return extractNs.group();
		else
			return null;
	}
}
