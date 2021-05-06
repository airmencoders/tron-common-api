package mil.tron.commonapi.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.method.configuration.GlobalMethodSecurityConfiguration;

import mil.tron.commonapi.repository.appsource.AppSourceRepository;

@Configuration
@ConditionalOnProperty(name = "security.enabled", havingValue="true")
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true, order = 1)
public class MethodSecurityConfig extends GlobalMethodSecurityConfiguration {

    @Bean
    public AccessCheck accessCheck(
        @Value("${api-prefix.v1}") String apiPrefix, 
        @Value("${app-sources-prefix}") String appSourcesPrefix) 
    {
        return new AccessCheckImpl(apiPrefix, appSourcesPrefix);
    }

    @Bean
    public AccessCheckAppSource accessCheckAppSource(AppSourceRepository appSourceRepository) {
        return new AccessCheckAppSourceImpl(appSourceRepository);
    }
}
