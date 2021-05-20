package mil.tron.commonapi.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.method.configuration.GlobalMethodSecurityConfiguration;

import mil.tron.commonapi.ApplicationProperties;
import mil.tron.commonapi.repository.appsource.AppSourceRepository;

@Configuration
@ConditionalOnProperty(name = "security.enabled", havingValue="true")
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true, order = 1)
public class MethodSecurityConfig extends GlobalMethodSecurityConfiguration {

    @Bean
    public AccessCheck accessCheck(ApplicationProperties versionProperties) 
    {
        return new AccessCheckImpl(versionProperties);
    }

    @Bean
    public AccessCheckAppSource accessCheckAppSource(AppSourceRepository appSourceRepository) {
        return new AccessCheckAppSourceImpl(appSourceRepository);
    }
}
