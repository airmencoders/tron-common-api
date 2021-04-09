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
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.appsource.AppEndpoint;
import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.repository.AppClientUserRespository;
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

    @Autowired
    private AppClientUserRespository appClientUserRepo;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        String uri = httpRequest.getRequestURI();
        String patternMatched = uri.replaceFirst("/api" + apiVersion + "/app/", "");
        
        // If uri starts with beginning prefix for AppSources
        String appSourcePath = patternMatched.substring(0, patternMatched.indexOf("/"));
        AppSource appSource = appSourceRepo.findByAppSourcePath(appSourcePath);
        if(appSource != null) {
            // If this belongs to an App Source, the rest of the path is part of the Endpoint
            // String path = String.join("/", Arrays.copyOfRange(uriParts, 5, uriParts.length));
            String name = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();
    
            AppEndpoint endpoint = appEndpointRepo.findByPathAndAppSource(patternMatched.substring(patternMatched.indexOf("/"), patternMatched.length()), appSource);
            AppClientUser appClient = appClientUserRepo.findByNameIgnoreCase(name).orElse(null);
            if(endpoint != null && appClient != null) {
                chain.doFilter(request, response);
                this.incrementCounter(endpoint, appSource, appClient);
                return;
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
                "AppSource", appSource.getAppSourcePath().replaceAll("/", "."), 
                "Endpoint", endpoint.getPath().replaceAll("/", "."), 
                "Path", path,
                "AppClient", appClient.getNameAsLower())
            .increment();
    }
}