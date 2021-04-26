package mil.tron.commonapi.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest
public class AccessCheckImplTest {
    
    @Test
    @WithMockUser(username="guardianangel", authorities = "appsource/endpoint_GET")
    public void passAuthenticationCheck() {
        AccessCheckImpl accessCheckImpl = new AccessCheckImpl("/v1");
        HttpServletRequest request = get("/v1/app/appsource/endpoint").buildRequest(null);
        assertTrue(accessCheckImpl.check(request));
    }

    @Test
    @WithMockUser(username="guardianangel", authorities = "appsource/endpoint_GET")
    public void passAuthenticationCheckWithParams() {
        AccessCheckImpl accessCheckImpl = new AccessCheckImpl("/v1");
        HttpServletRequest request = get("/v1/app/appsource/endpoint?test=test").buildRequest(null);
        assertTrue(accessCheckImpl.check(request));
    }

    @Test
    @WithMockUser(username="guardianangel", authorities = "appsource/endpoint_GET")
    public void failAuthenticationCheck() {
        AccessCheckImpl accessCheckImpl = new AccessCheckImpl("/v1");
        HttpServletRequest request = get("/v1/app/appsource/endpoint_fail").buildRequest(null);
        assertFalse(accessCheckImpl.check(request));
    }
}
