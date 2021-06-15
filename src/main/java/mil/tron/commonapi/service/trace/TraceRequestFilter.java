package mil.tron.commonapi.service.trace;

import org.springframework.boot.actuate.trace.http.HttpExchangeTracer;
import org.springframework.boot.actuate.trace.http.HttpTraceRepository;
import org.springframework.boot.actuate.web.trace.servlet.HttpTraceFilter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * This configures the HttpTracer to NOT log requests to specific endpoints - which we really don't care about,
 * or they are just reachable in a dev environment.
 */
@Component
//@Profile("production | development")
public class TraceRequestFilter extends HttpTraceFilter {

    public TraceRequestFilter(HttpTraceRepository repository, HttpExchangeTracer tracer) {
        super(repository, tracer);
    }

    /**
     * Dont log stuff from actuator, http log audit, h2-console, or from swagger paths
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getServletPath().contains("actuator")
                || request.getServletPath().contains("api-docs")
                || request.getServletPath().contains("/logs")
                || request.getServletPath().contains("h2-console");
    }
}
