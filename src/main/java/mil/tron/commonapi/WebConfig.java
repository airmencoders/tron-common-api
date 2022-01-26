package mil.tron.commonapi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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


//    @Bean
//    public DefaultHttpFirewall httpFirewall() {
//        DefaultHttpFirewall firewall = new DefaultHttpFirewall();
//        return firewall;
//    }

    /**
     * Log to the console any firewall rejected events
     * @return
     */
//    @Bean
//    public RequestRejectedHandler requestRejectedHandler() {
//        return (httpServletRequest, httpServletResponse, e) -> {
//            log.warn(
//                    "request_rejected: remote={}, user_agent={}, request_url={}, exception={}",
//                    httpServletRequest.getRemoteHost(),
//                    httpServletRequest.getHeader(HttpHeaders.USER_AGENT),
//                    httpServletRequest.getRequestURL(),
//                    e.getMessage()
//            );
//            httpServletResponse.sendError(HttpServletResponse.SC_BAD_REQUEST);
//        };
//    }
}
