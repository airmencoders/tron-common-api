package mil.tron.commonapi.security;

import static org.assertj.core.api.Assertions.assertThat;

import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AppClientPreAuthFilterTest {
	private static final String NAMESPACE = "testnamespacename";
	private static final String XFCC_BY = "By=spiffe://cluster/ns/tron-common-api/sa/default";
	private static final String XFCC_H = "FAKE_H=12345";
	private static final String XFCC_SUBJECT = "Subject=\\\"\\\";";
	private static final String XFCC_HEADER = new StringBuilder()
			.append(XFCC_BY)
			.append(XFCC_H)
			.append(XFCC_SUBJECT)
			.append("URI=spiffe://cluster.local/ns/" + NAMESPACE + "/sa/default")
			.toString();
	
	private static final String XFCC_HEADER_MALFORMED = new StringBuilder()
			.append(XFCC_BY)
			.append(XFCC_H)
			.append(XFCC_SUBJECT)
			.append("spiffe://cluster.local/ns/" + NAMESPACE + "/sa/default")
			.toString();
	
	private static final String XFCC_HEADER_NO_URI = new StringBuilder()
			.append(XFCC_BY)
			.append(XFCC_H)
			.append(XFCC_SUBJECT)
			.toString();
	
	private static final String XFCC_HEADER_BLANK_URI = new StringBuilder()
			.append(XFCC_BY)
			.append(XFCC_H)
			.append(XFCC_SUBJECT)
			.append("URI=")
			.toString();
	
	private static final String XFCC_HEADER_MALFORMED_URI = new StringBuilder()
			.append(XFCC_BY)
			.append(XFCC_H)
			.append(XFCC_SUBJECT)
			.append("URI=spiffe://cluster.local/" + NAMESPACE + "/sa/default")
			.toString();
	
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
