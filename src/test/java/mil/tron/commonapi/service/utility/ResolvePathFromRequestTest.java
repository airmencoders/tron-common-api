package mil.tron.commonapi.service.utility;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@ExtendWith(SpringExtension.class)
public class ResolvePathFromRequestTest {
    
    @Test
    public void returnsTrimmedPath() {
        HttpServletRequest request = get("/v1/app/appsource/endpoint").buildRequest(null);
        assertEquals("appsource/endpoint", ResolvePathFromRequest.resolve(request, "/v1/app"));
    }

    @Test
    public void returnsFullPathWithNoPrefix() {
        HttpServletRequest request = get("/api/v1/app/appsource/endpoint").buildRequest(null);
        assertEquals("/api/v1/app/appsource/endpoint", ResolvePathFromRequest.resolve(request, null));
    }

    @Test
    public void returnsTrimmedPathWithContextPathPrefix() {
        HttpServletRequest request = get("/api/v1/app/appsource/endpoint").buildRequest(null);
        assertEquals("appsource/endpoint", ResolvePathFromRequest.resolve(request, "/v1/app"));
    }

    @Test
    public void handlesNullRequestObject() {
        assertEquals("", ResolvePathFromRequest.resolve(null, "/v1/app"));
    }
}
