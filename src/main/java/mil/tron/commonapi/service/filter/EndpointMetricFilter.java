package mil.tron.commonapi.service.filter;

import io.micrometer.core.instrument.MeterRegistry;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.appsource.AppEndpoint;
import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.appsource.AppEndpointRepository;
import mil.tron.commonapi.repository.appsource.AppSourceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@Component
@ConditionalOnProperty("metrics.gateway.count")
public class EndpointMetricFilter implements Filter {
    
    @Value("${api-prefix.v1}")
    private String apiVersion;
    
    @Value("${app-sources-prefix}")
    private String appSourcesPrefix;

    @Autowired
    private AppEndpointRepository appEndpointRepo;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private AppSourceRepository appSourceRepo;

    @Autowired
    private AppClientUserRespository appClientUserRepo;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        String uri = httpRequest.getRequestURI();
        String patternMatched = uri.replaceFirst("/api" + apiVersion + appSourcesPrefix + "/", "");
        
        int separator = patternMatched.indexOf("/");
        if (separator > -1) {
            // If uri starts with beginning prefix for AppSources
            String appSourcePath = patternMatched.substring(0, separator);
            AppSource appSource = appSourceRepo.findByAppSourcePath(appSourcePath);
            if(appSource != null) {
                // If this belongs to an App Source, the rest of the path is part of the Endpoint
                String name = SecurityContextHolder
                    .getContext()
                    .getAuthentication()
                    .getName();
        
                AppEndpoint endpoint = appEndpointRepo.findByPathAndAppSourceAndMethod(patternMatched.substring(separator, patternMatched.length()), appSource, RequestMethod.valueOf(httpRequest.getMethod()));
                AppClientUser appClient = appClientUserRepo.findByNameIgnoreCase(name).orElse(null);
                if(endpoint != null && appClient != null) {
                    chain.doFilter(request, response);
                    this.incrementCounter(endpoint, appSource, appClient);
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }

    // register counter with tags AppSource, Endpoint, and AppClient so we can find it again
    private void incrementCounter(AppEndpoint endpoint, AppSource appSource, AppClientUser appClient) {
        String path = (appSource.getAppSourcePath() + "." + endpoint.getPath()).replaceAll("/", ".");       
        meterRegistry
            .counter(
                "gateway-counter." + appSource.getAppSourcePath().replaceAll("/", "."), 
                "AppSource", appSource.getId().toString(), 
                "Endpoint", endpoint.getId().toString(), 
                "Path", path,
                "AppClient", appClient.getId().toString())
            .increment();
    }
}