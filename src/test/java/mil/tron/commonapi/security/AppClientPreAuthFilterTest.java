package mil.tron.commonapi.security;

import static org.assertj.core.api.Assertions.assertThat;

import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AppClientPreAuthFilterTest {
	private static final String NAMESPACE = "testnamespacename";
	private static final String XFCC_HEADER = "By=spiffe://cluster/ns/tron-common-api/sa/default;Hash=12345252036c835ac9b174bbfaef9e4a07dbc5691ef3d295bb8b815762554321;Subject=\"\";URI=spiffe://cluster.local/ns/" + NAMESPACE + "/sa/default";
	private static final String XFCC_HEADER_MALFORMED = "By=spiffe://cluster/ns/tron-common-api/sa/default;Hash=12345252036c835ac9b174bbfaef9e4a07dbc5691ef3d295bb8b815762554321;Subject=\"\";spiffe://cluster.local/ns/" + NAMESPACE + "/sa/default";
	private static final String XFCC_HEADER_NO_URI = "By=spiffe://cluster/ns/tron-common-api/sa/default;Hash=12345252036c835ac9b174bbfaef9e4a07dbc5691ef3d295bb8b815762554321;Subject=\"\"";
	private static final String XFCC_HEADER_BLANK_URI = "By=spiffe://cluster/ns/tron-common-api/sa/default;Hash=12345252036c835ac9b174bbfaef9e4a07dbc5691ef3d295bb8b815762554321;Subject=\"\";URI=";
	private static final String XFCC_HEADER_MALFORMED_URI = "By=spiffe://cluster/ns/tron-common-api/sa/default;Hash=12345252036c835ac9b174bbfaef9e4a07dbc5691ef3d295bb8b815762554321;Subject=\"\";URI=spiffe://cluster.local/" + NAMESPACE + "/sa/default";
	
	private AppClientPreAuthFilter filter = new AppClientPreAuthFilter();
	
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
	
    @Nested
    class TestPrincipal {
    	@Test
    	void testNullXfcc() {
    		Mockito.when(request.getHeader("x-forwarded-client-cert")).thenReturn(null);
    		
    		String result = (String) filter.getPreAuthenticatedPrincipal(request);
    		
    		assertThat(result).isNull();
    	}
    	
    	@Test
    	void testBlankXfcc() {
    		Mockito.when(request.getHeader("x-forwarded-client-cert")).thenReturn("   ");
    		
    		String result = (String) filter.getPreAuthenticatedPrincipal(request);
    		
    		assertThat(result).isNull();
    	}
    	
    	@Test
    	void testValidXfcc() {
    		Mockito.when(request.getHeader("x-forwarded-client-cert")).thenReturn(XFCC_HEADER);
    		
    		String result = (String) filter.getPreAuthenticatedPrincipal(request);
    		
    		assertThat(result).isEqualTo(NAMESPACE);
    	}
    	
    	@Test
    	void testMalformedXfcc() {
    		Mockito.when(request.getHeader("x-forwarded-client-cert")).thenReturn(XFCC_HEADER_MALFORMED);
    		
    		String result = (String) filter.getPreAuthenticatedPrincipal(request);
    		
    		assertThat(result).isNull();
    	}
    	
    	@Test
    	void testNoUriXfcc() {
    		Mockito.when(request.getHeader("x-forwarded-client-cert")).thenReturn(XFCC_HEADER_NO_URI);
    		
    		String result = (String) filter.getPreAuthenticatedPrincipal(request);
    		
    		assertThat(result).isNull();
    	}
    	
    	@Test
    	void testBlankUriXfcc() {
    		Mockito.when(request.getHeader("x-forwarded-client-cert")).thenReturn(XFCC_HEADER_BLANK_URI);
    		
    		String result = (String) filter.getPreAuthenticatedPrincipal(request);
    		
    		assertThat(result).isNull();
    	}
    	
    	@Test
    	void testInvalidUriStructureXfcc() {
    		Mockito.when(request.getHeader("x-forwarded-client-cert")).thenReturn(XFCC_HEADER_MALFORMED_URI);
    		
    		String result = (String) filter.getPreAuthenticatedPrincipal(request);
    		
    		assertThat(result).isNull();
    	}
    }
	
    @Nested
    class TestCredentials {
    	@Test
    	void testGetCredentials() {
    		Mockito.when(request.getHeader("x-forwarded-client-cert")).thenReturn(XFCC_HEADER);
    		
    		String result = (String) filter.getPreAuthenticatedCredentials(request);
    		
    		assertThat(result).isEqualTo("N/A");
    	}
    }
}
