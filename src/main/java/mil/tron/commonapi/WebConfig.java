package mil.tron.commonapi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.firewall.HttpStatusRequestRejectedHandler;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;

@Configuration
@Slf4j
@PropertySource("classpath:application.properties")
public class WebConfig implements WebMvcConfigurer {

    public static final String[] allowedMethods = new String[] {
        "GET", "PUT", "POST", "DELETE", "OPTIONS", "PATCH", "LOCK", "UNLOCK", "HEAD", "PROPFIND", "MKCOL"
    };

    @Override
    public void addCorsMappings(CorsRegistry registry) {

        registry.addMapping("/**").allowedOriginPatterns("*").allowedHeaders("*")
                .allowedMethods(allowedMethods).allowCredentials(true);

    }

    @Bean
    public StrictHttpFirewall httpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowedHttpMethods(Arrays.asList(allowedMethods));
        return firewall;
    }

    @Bean
    public RequestRejectedHandler requestRejectedHandler() {
        return (httpServletRequest, httpServletResponse, e) -> {
            log.warn(
                    "request_rejected: remote={}, queryParms={}, jwt={}, user_agent={}, request_url={}",
                    httpServletRequest.getRemoteHost(),
                    httpServletRequest.getQueryString(),
                    httpServletRequest.getHeader("authorization"),
                    httpServletRequest.getHeader(HttpHeaders.USER_AGENT),
                    httpServletRequest.getRequestURL(),
                    e
            );
            httpServletResponse.sendError(HttpServletResponse.SC_BAD_REQUEST);
        };
    }
}
