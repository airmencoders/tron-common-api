package mil.tron.commonapi.service.utility;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.Arrays;

@ExtendWith(SpringExtension.class)
public class ResolvePathFromRequestTest {
    
    @Test
    public void returnsTrimmedPath() {
        HttpServletRequest request = get("/v1/app/appsource/endpoint").buildRequest(null);
        assertEquals("appsource/endpoint", ResolvePathFromRequest.resolve(request, Arrays.asList("/v1/app")));
    }

    @Test
    public void returnsTrimmedPathWhenMultipleOptions() {
        HttpServletRequest request = get("/v2/app/appsource/endpoint").buildRequest(null);
        assertEquals("appsource/endpoint", ResolvePathFromRequest.resolve(request, Arrays.asList("/v1/app", "/v2/app")));
    }

    @Test
    public void returnsFullPathWithNullPrefixesList() {
        HttpServletRequest request = get("/api/v1/app/appsource/endpoint").buildRequest(null);
        assertEquals("/api/v1/app/appsource/endpoint", ResolvePathFromRequest.resolve(request, null));
    }

    @Test
    public void returnsFullPathWithEmptyPrefixesList() {
        HttpServletRequest request = get("/api/v1/app/appsource/endpoint").buildRequest(null);
        assertEquals("/api/v1/app/appsource/endpoint", ResolvePathFromRequest.resolve(request, Arrays.asList()));
    }

    @Test
    public void returnsFullPathWhenMultipleOptionsButNoneMatch() {
        HttpServletRequest request = get("/v3/app/appsource/endpoint").buildRequest(null);
        assertEquals("/v3/app/appsource/endpoint", ResolvePathFromRequest.resolve(request, Arrays.asList("/v1/app", "/v2/app")));
    }

    @Test
    public void returnsTrimmedPathWithContextPathPrefix() {
        HttpServletRequest request = get("/api/v1/app/appsource/endpoint").buildRequest(null);
        assertEquals("appsource/endpoint", ResolvePathFromRequest.resolve(request, Arrays.asList("/v1/app")));
    }

    @Test
    public void handlesNullRequestObject() {
        assertEquals("", ResolvePathFromRequest.resolve(null, Arrays.asList("/v1/app")));
    }
}
