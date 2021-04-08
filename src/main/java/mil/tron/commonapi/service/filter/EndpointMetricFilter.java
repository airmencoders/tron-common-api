package mil.tron.commonapi.service.filter;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import mil.tron.commonapi.CustomMeterRegistry;
import mil.tron.commonapi.entity.appsource.AppEndpoint;
import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.repository.appsource.AppEndpointRepository;
import mil.tron.commonapi.repository.appsource.AppSourceRepository;

@Component
public class EndpointMetricFilter implements Filter {
    
    @Value("${api-prefix.v1}")
    private String apiVersion;

    @Autowired
    private AppEndpointRepository appEndpointRepo;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private AppSourceRepository appSourceRepo;

    // @Override
    // public void init(FilterConfig config) throws ServletException {
    //     metricService = (MetricService) WebApplicationContextUtils
    //         .getRequiredWebApplicationContext(config.getServletContext())
    //         .getBean("metricService");
    // }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        // String[] uriParts  = httpRequest.getRequestURI().split("/");
        // get the spring-matched request mapping -- trim off the beginning prefix (e.g. /v1/app/)
        // String uri = httpRequest
        //     .getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)
        //     .toString();
        String uri = httpRequest.getRequestURI();
        String patternMatched = uri.replaceFirst("/api" + apiVersion + "/app/", "");
        
        // If uri starts with beginning prefix for AppSources
        String appSourcePath = patternMatched.substring(0, patternMatched.indexOf("/"));
        AppSource appSource = appSourceRepo.findByAppSourcePath(appSourcePath);
        if(appSource != null) {
            // If this belongs to an App Source, the rest of the path is part of the Endpoint
            // String path = String.join("/", Arrays.copyOfRange(uriParts, 5, uriParts.length));
    
            AppEndpoint endpoint = appEndpointRepo.findByPathAndAppSource(patternMatched.substring(patternMatched.indexOf("/"), patternMatched.length()), appSource);
            if(endpoint != null) {
                Sample sample = Timer.start();
                chain.doFilter(request, response);
                sample.stop(getTimer(endpoint, appSource));
                return;
            }
        }
        chain.doFilter(request, response);
    }

    // register timer on AppSource, Endpoint, and AppClient so we can find it again
    private Timer getTimer(AppEndpoint endpoint, AppSource appSource) {
        String path = (appSource.getAppSourcePath() + "." + endpoint.getPath()).replaceAll("/", ".");
        Object obj = meterRegistry.config();
        String name = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();
        return meterRegistry.timer("gateway." + appSource.getAppSourcePath().replaceAll("/", "."), 
            "AppSource", appSource.getAppSourcePath().replaceAll("/", "."), 
            "Endpoint", endpoint.getPath().replaceAll("/", "."), 
            "Path", path,
            "AppClient", name);
    }
}