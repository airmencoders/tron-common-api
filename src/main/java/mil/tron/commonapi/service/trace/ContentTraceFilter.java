package mil.tron.commonapi.service.trace;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import mil.tron.commonapi.controller.documentspace.DocumentSpaceController;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Custom filter that caches and wraps the ServletRequest/Response so that we can read its contents
 * while still allowing it to be forwarded to the controllers for reading.
 * Adapted from https://developer.okta.com/blog/2019/07/17/monitoring-with-actuator
 *
 * This bean not active in unit tests since it would require stubbing of it all throughout, rather
 * its just on during dev/prod profiles and in the integration tests.
 */
@Component
@Profile("production | development | staging")
public class ContentTraceFilter extends OncePerRequestFilter {
    private ContentTraceManager traceManager;

    @Autowired
    public ContentTraceFilter(ContentTraceManager traceManager) {
        super();
        this.traceManager = traceManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
    	
    	/**
    	 * Interaction between {@link StreamingResponseBody} on the Controller & {@link ContentCachingResponseWrapper}
    	 * causes behavior in which what appears to happen is that the output stream will be read, causing
    	 * empty responses. So skip the filter if the request matches specifically to the Document Space endpoint.
    	 */
        if (!isRequestValid(request) || DocumentSpaceController.DOCUMENT_SPACE_PATTERN.asPredicate().test(request.getServletPath())) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request, 64000);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        }
        finally {
            traceManager.updateBody(wrappedRequest, wrappedResponse);
            wrappedResponse.copyBodyToResponse();
        }
    }

    private boolean isRequestValid(HttpServletRequest request) {
        try {
            new URI(request.getRequestURL().toString());
            return true;
        } catch (URISyntaxException ex) {
            return false;
        }
    }

}