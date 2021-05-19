package mil.tron.commonapi.service.trace;

import org.springframework.boot.actuate.trace.http.HttpExchangeTracer;
import org.springframework.boot.actuate.trace.http.HttpTraceRepository;
import org.springframework.boot.actuate.web.trace.servlet.HttpTraceFilter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

@Component
@Profile("production | development")
public class TraceRequestFilter extends HttpTraceFilter {

    public TraceRequestFilter(HttpTraceRepository repository, HttpExchangeTracer tracer) {
        super(repository, tracer);
    }

    /**
     * Dont log stuff from actuator, h2-console, or from swagger paths
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getServletPath().contains("actuator")
                || request.getServletPath().contains("api-docs")
                || request.getServletPath().contains("h2-console");
    }
}
