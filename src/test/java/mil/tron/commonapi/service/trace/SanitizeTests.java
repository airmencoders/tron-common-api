package mil.tron.commonapi.service.trace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.trace.http.HttpTrace;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.URISyntaxException;

@SpringBootTest
public class SanitizeTests {

    @Autowired
    private HttpTraceService httpTraceService;

    @Test
    void testSanitize() throws URISyntaxException {

        HttpTrace.Request request = new HttpTrace.Request("POST",
                new URI("https://tron-common-api-il4.apps.dso.mil/api/app/arms-gateway/training-svc/"),
                null,
                null);

        HttpTrace trace = new HttpTrace(request, null, null, null, null, 1000L);

        ContentTrace contentTrace = new ContentTrace();
        contentTrace.setRequestBody("some sensitive stuff");
        contentTrace.setResponseBody("some sensitive stuff");

        httpTraceService.sanitizeBodies(trace, contentTrace);
    }


}
