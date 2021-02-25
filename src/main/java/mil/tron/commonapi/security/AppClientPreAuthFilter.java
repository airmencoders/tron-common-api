package mil.tron.commonapi.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppClientPreAuthFilter extends AbstractPreAuthenticatedProcessingFilter  {

	public static final String XFCC_HEADER_NAME = "x-forwarded-client-cert";
	private static final String NAMESPACE_REGEX = "(?<=\\/ns\\/)([^\\/]*)";
	private static final Pattern NAMESPACE_PATTERN = Pattern.compile(NAMESPACE_REGEX);
	private static final String NO_CRED = "NoCredentials";
	
	@Override
	protected Object getPreAuthenticatedPrincipal(HttpServletRequest request) {
		String header = request.getHeader(XFCC_HEADER_NAME);
		String uri = extractUriFromXfccHeader(header);

		return extractNamespaceFromUri(uri);
	}

	private DecodedJWT decodeJwt (HttpServletRequest request) {
		String bearer = request.getHeader("authorization");
		return JWT.decode(bearer.split("Bearer ")[1]);
	}

	@Override
	protected Object getPreAuthenticatedCredentials(HttpServletRequest request) {
		if (request == null || request.getHeaderNames() == null) return NO_CRED;
		Enumeration<String> headerNames = request.getHeaderNames();
		while (headerNames.hasMoreElements()){
			if (headerNames.nextElement().equals("authorization")){
				DecodedJWT decodedJwt = decodeJwt(request);
				return decodedJwt.getClaim("email").asString();
			}
		}
		return NO_CRED;
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
