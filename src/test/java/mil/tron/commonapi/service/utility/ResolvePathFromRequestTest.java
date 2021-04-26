package mil.tron.commonapi.service.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
public class ResolvePathFromRequestTest {
    
    @Test
    public void returnsTrimmedPath() {
        HttpServletRequest request = get("/v1/app/appsource/endpoint").buildRequest(null);
        assertEquals(ResolvePathFromRequest.resolve(request, "/v1"), "appsource/endpoint");
    }

    @Test
    public void returnsFullPathWithNoPrefix() {
        HttpServletRequest request = get("/v1/app/appsource/endpoint").buildRequest(null);
        assertEquals(ResolvePathFromRequest.resolve(request, null), "/v1/app/appsource/endpoint");
    }

    @Test
    public void handlesNullRequestObject() {
        assertEquals(ResolvePathFromRequest.resolve(null, "/v1"), "");
    }
}
