package mil.tron.commonapi.service.trace;

import mil.tron.commonapi.logging.CommonApiLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Component
@RequestScope
@Profile("production | development")
public class ContentTraceManager {

    private ContentTrace trace;

    public ContentTraceManager(ContentTrace trace) {
        this.trace=trace;
    }

    protected static Logger logger = LoggerFactory.getLogger(CommonApiLogger.class);

    public void updateBody(ContentCachingRequestWrapper wrappedRequest,
                           ContentCachingResponseWrapper wrappedResponse) {

        String requestBody = getRequestBody(wrappedRequest);
        getTrace().setRequestBody(requestBody);

        String responseBody = getResponseBody(wrappedResponse);
        getTrace().setResponseBody(responseBody);
    }

    protected String getRequestBody(
            ContentCachingRequestWrapper wrappedRequest) {

            if (wrappedRequest.getContentLength() <= 0) {
                return null;
            }
            return new String(wrappedRequest.getContentAsByteArray());


    }

    protected String getResponseBody(
            ContentCachingResponseWrapper wrappedResponse) {

        if (wrappedResponse.getContentSize() <= 0) {
            return null;
        }
        return new String(wrappedResponse.getContentAsByteArray());
    }

    public ContentTrace getTrace() {
        if (trace == null) {
            trace = new ContentTrace();
        }
        return trace;
    }
}
