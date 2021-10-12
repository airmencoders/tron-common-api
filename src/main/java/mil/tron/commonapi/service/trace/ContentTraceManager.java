package mil.tron.commonapi.service.trace;

import mil.tron.commonapi.exception.BadRequestException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.UnsupportedEncodingException;

/**
 * Bean that takes care of updating our ContentTrace object that is peculiar to this current request
 * (hence the @RequestScope annotation).
 * Adapted from https://developer.okta.com/blog/2019/07/17/monitoring-with-actuator
 */
@Component
@RequestScope
@Profile("production | development | staging | local")
public class ContentTraceManager {

    private ContentTrace trace;
    public ContentTraceManager(ContentTrace trace) {
        this.trace=trace;
    }

    public void updateBody(ContentCachingRequestWrapper wrappedRequest, ContentCachingResponseWrapper wrappedResponse) {
        String requestBody = getRequestBody(wrappedRequest);
        getTrace().setRequestBody(requestBody);

        String responseBody = getResponseBody(wrappedResponse);
        getTrace().setResponseBody(responseBody);
    }

    public void setErrorMessage(String body) {
        getTrace().setErrorMessage(body);
    }

    protected String getRequestBody(ContentCachingRequestWrapper wrappedRequest) {
        try {
            if (wrappedRequest.getContentLength() <= 0) {
                return null;
            }
            return new String(wrappedRequest.getContentAsByteArray(), wrappedRequest.getCharacterEncoding());  //NOSONAR
        }
        catch (UnsupportedEncodingException e) {
            throw new BadRequestException("Unsupported character encoding in request");
        }


    }

    protected String getResponseBody(ContentCachingResponseWrapper wrappedResponse) {
        try {
            if (wrappedResponse.getContentSize() <= 0) {
                return null;
            }
            return new String(wrappedResponse.getContentAsByteArray(), wrappedResponse.getCharacterEncoding());  //NOSONAR
        }
        catch (UnsupportedEncodingException e) {
            throw new BadRequestException("Unsupported character encoding in request");
        }
    }

    public ContentTrace getTrace() {
        if (trace == null) {
            trace = new ContentTrace();
        }
        return trace;
    }
}
