package mil.tron.commonapi;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
@PropertySource("classpath:application.properties")
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {

        registry.addMapping("/**").allowedOriginPatterns("*").allowedHeaders("*")
                .allowedMethods("GET", "PUT", "POST", "DELETE", "OPTIONS", "PATCH", "PROPFIND").allowCredentials(true);

    }

    @Bean
    public StrictHttpFirewall httpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowedHttpMethods(Arrays.asList("GET", "PUT", "POST", "DELETE", "OPTIONS", "PATCH", "PROPFIND"));
        return firewall;
    }
}
