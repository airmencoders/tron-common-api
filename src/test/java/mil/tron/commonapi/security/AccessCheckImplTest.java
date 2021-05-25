package mil.tron.commonapi.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

import mil.tron.commonapi.ApplicationProperties;

@SpringBootTest
public class AccessCheckImplTest {
    
    @Test
    @WithMockUser(username="guardianangel", authorities = "appsource/endpoint_GET")
    public void passAuthenticationCheck() {
        ApplicationProperties prefixProperties = Mockito.mock(ApplicationProperties.class);        
        when(prefixProperties.getCombinedPrefixes()).thenReturn(Arrays.asList("/v1/app"));

        AccessCheckImpl accessCheckImpl = new AccessCheckImpl(prefixProperties);
        HttpServletRequest request = get("/v1/app/appsource/endpoint").buildRequest(null);
        assertTrue(accessCheckImpl.check(request));
    }

    @Test
    @WithMockUser(username="guardianangel", authorities = "appsource/endpoint_GET")
    public void passAuthenticationCheckWithMultipleVersionPossibilities() {
        ApplicationProperties prefixProperties = Mockito.mock(ApplicationProperties.class);        
        when(prefixProperties.getCombinedPrefixes()).thenReturn(Arrays.asList("/v1/app", "/v2/app"));

        AccessCheckImpl accessCheckImpl = new AccessCheckImpl(prefixProperties);
        HttpServletRequest request = get("/v2/app/appsource/endpoint").buildRequest(null);
        assertTrue(accessCheckImpl.check(request));
    }

    @Test
    @WithMockUser(username="guardianangel", authorities = "appsource/endpoint_GET")
    public void passAuthenticationCheckWithServletContextExplictlyDenoted() {
        ApplicationProperties prefixProperties = Mockito.mock(ApplicationProperties.class);        
        when(prefixProperties.getCombinedPrefixes()).thenReturn(Arrays.asList("/v1/app"));

        AccessCheckImpl accessCheckImpl = new AccessCheckImpl(prefixProperties);
        HttpServletRequest request = get("/api/v1/app/appsource/endpoint").buildRequest(null);
        assertTrue(accessCheckImpl.check(request));
    }

    @Test
    @WithMockUser(username="guardianangel", authorities = "appsource/endpoint_GET")
    public void passAuthenticationCheckWithParams() {
        ApplicationProperties prefixProperties = Mockito.mock(ApplicationProperties.class);        
        when(prefixProperties.getCombinedPrefixes()).thenReturn(Arrays.asList("/v1/app"));
        
        AccessCheckImpl accessCheckImpl = new AccessCheckImpl(prefixProperties);
        HttpServletRequest request = get("/v1/app/appsource/endpoint?test=test").buildRequest(null);
        assertTrue(accessCheckImpl.check(request));
    }

    @Test
    @WithMockUser(username="guardianangel", authorities = "appsource/endpoint_GET")
    public void failAuthenticationCheck() {
        ApplicationProperties prefixProperties = Mockito.mock(ApplicationProperties.class);        
        when(prefixProperties.getCombinedPrefixes()).thenReturn(Arrays.asList("/v1/app"));
        
        AccessCheckImpl accessCheckImpl = new AccessCheckImpl(prefixProperties);
        HttpServletRequest request = get("/v1/app/appsource/endpoint_fail").buildRequest(null);
        assertFalse(accessCheckImpl.check(request));
    }
}
