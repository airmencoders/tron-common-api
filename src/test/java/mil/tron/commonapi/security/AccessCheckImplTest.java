package mil.tron.commonapi.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.bind.annotation.RequestMethod;

import mil.tron.commonapi.ApplicationProperties;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.appsource.AppEndpoint;
import mil.tron.commonapi.entity.appsource.AppEndpointPriv;
import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.service.AppClientUserService;

@SpringBootTest
public class AccessCheckImplTest {
	@MockBean
	private AppClientUserService userService;
    
    @Test
    @WithMockUser(username="guardianangel", authorities = "appsource/endpoint_GET")
    public void passAuthenticationCheck() {
        ApplicationProperties prefixProperties = Mockito.mock(ApplicationProperties.class);        
        when(prefixProperties.getCombinedPrefixes()).thenReturn(Arrays.asList("/v1/app"));

        AccessCheckImpl accessCheckImpl = new AccessCheckImpl(prefixProperties, userService);
        HttpServletRequest request = get("/v1/app/appsource/endpoint").buildRequest(null);
        assertTrue(accessCheckImpl.check(request));
    }

    @Test
    @WithMockUser(username="guardianangel", authorities = "appsource/endpoint_GET")
    public void passAuthenticationCheckWithMultipleVersionPossibilities() {
        ApplicationProperties prefixProperties = Mockito.mock(ApplicationProperties.class);        
        when(prefixProperties.getCombinedPrefixes()).thenReturn(Arrays.asList("/v1/app", "/v2/app"));

        AccessCheckImpl accessCheckImpl = new AccessCheckImpl(prefixProperties, userService);
        HttpServletRequest request = get("/v2/app/appsource/endpoint").buildRequest(null);
        assertTrue(accessCheckImpl.check(request));
    }

    @Test
    @WithMockUser(username="guardianangel", authorities = "appsource/endpoint_GET")
    public void passAuthenticationCheckWithServletContextExplictlyDenoted() {
        ApplicationProperties prefixProperties = Mockito.mock(ApplicationProperties.class);        
        when(prefixProperties.getCombinedPrefixes()).thenReturn(Arrays.asList("/v1/app"));

        AccessCheckImpl accessCheckImpl = new AccessCheckImpl(prefixProperties, userService);
        HttpServletRequest request = get("/api/v1/app/appsource/endpoint").buildRequest(null);
        assertTrue(accessCheckImpl.check(request));
    }

    @Test
    @WithMockUser(username="guardianangel", authorities = "appsource/endpoint_GET")
    public void passAuthenticationCheckWithParams() {
        ApplicationProperties prefixProperties = Mockito.mock(ApplicationProperties.class);        
        when(prefixProperties.getCombinedPrefixes()).thenReturn(Arrays.asList("/v1/app"));
        
        AccessCheckImpl accessCheckImpl = new AccessCheckImpl(prefixProperties, userService);
        HttpServletRequest request = get("/v1/app/appsource/endpoint?test=test").buildRequest(null);
        assertTrue(accessCheckImpl.check(request));
    }
    
    @Test
    @WithMockUser(username="guardianangel", authorities = "appsource/endpoint/{pathParam}_GET")
    public void passAuthenticationCheckWithPathParams() {
        ApplicationProperties prefixProperties = Mockito.mock(ApplicationProperties.class);        
        when(prefixProperties.getCombinedPrefixes()).thenReturn(Arrays.asList("/v1/app"));
        
        AccessCheckImpl accessCheckImpl = new AccessCheckImpl(prefixProperties, userService);
        HttpServletRequest request = get("/v1/app/appsource/endpoint/1").buildRequest(null);
        assertTrue(accessCheckImpl.check(request));
    }

    @Test
    @WithMockUser(username="guardianangel", authorities = "appsource/endpoint_GET")
    public void failAuthenticationCheck() {
        ApplicationProperties prefixProperties = Mockito.mock(ApplicationProperties.class);        
        when(prefixProperties.getCombinedPrefixes()).thenReturn(Arrays.asList("/v1/app"));
        
        AccessCheckImpl accessCheckImpl = new AccessCheckImpl(prefixProperties, userService);
        HttpServletRequest request = get("/v1/app/appsource/endpoint_fail").buildRequest(null);
        assertFalse(accessCheckImpl.check(request));
    }
    
    @Test
    @WithMockUser(username="test@app.dev", authorities = "APP_CLIENT_DEVELOPER")
    public void passAuthenticationAsAppClientDeveloper() {
        ApplicationProperties prefixProperties = Mockito.mock(ApplicationProperties.class);        
        when(prefixProperties.getCombinedPrefixes()).thenReturn(Arrays.asList("/v1/app"));
        
        
        AppSource appSource = AppSource.builder().appSourcePath("appsource").build();
        
        AppClientUser appClient = AppClientUser.builder()
				.appClientDevelopers(Set.of(new DashboardUser(UUID.randomUUID(), "test@app.dev", "test@app.dev",
						Set.of(Privilege.builder().id(1L).name("APP_CLIENT_DEVELOPER").build()), Set.of(), Set.of(), UUID.randomUUID())))
        		.appEndpointPrivs(Set.of(
        					AppEndpointPriv.builder()
        						.appEndpoint(AppEndpoint.builder()
        								.appSource(appSource)
        								.path("/endpoint")
        								.method(RequestMethod.GET)
        								.build())
        						.build())
        				)
        		.build();
        when(userService.getAppClientUsersContainingDeveloperEmail(Mockito.anyString())).thenReturn(List.of(appClient));
        
        AccessCheckImpl accessCheckImpl = new AccessCheckImpl(prefixProperties, userService);
        HttpServletRequest request = get("/v1/app/appsource/endpoint").buildRequest(null);
        assertTrue(accessCheckImpl.check(request));
    }
}
