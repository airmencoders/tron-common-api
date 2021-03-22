package mil.tron.commonapi;

import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@PropertySource("classpath:application.properties")
public class WebConfig implements WebMvcConfigurer {

    @Value("${origins}")
    private String origins;

    @Value("${scratch-origin}")
    private String scratchOrigin;

    @Override
    public void addCorsMappings(CorsRegistry registry) {

        // list of origins for everything but scratch space
        String[] urlOrigins = origins.split(",");

        // list of origins for scratch space
        List<String> scratchUrlList = Lists.asList(scratchOrigin, urlOrigins);
        String[] scratchUrlOrigins = new String[urlOrigins.length + 1];
        scratchUrlOrigins = scratchUrlList.toArray(scratchUrlOrigins);

        registry.addMapping("/v1/scratch")
                .allowedMethods("*")
                .allowedOriginPatterns(scratchUrlOrigins);

        registry.addMapping("/**")
                .allowedMethods("*")
                .allowedOrigins(origins.split(","));
    }
}
