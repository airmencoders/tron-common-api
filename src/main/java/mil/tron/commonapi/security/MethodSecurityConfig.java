package mil.tron.commonapi.security;

import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.appsource.AppEndpointPrivRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.method.configuration.GlobalMethodSecurityConfiguration;

import mil.tron.commonapi.ApplicationProperties;
import mil.tron.commonapi.repository.appsource.AppSourceRepository;
import mil.tron.commonapi.service.AppClientUserService;

@Configuration
@ConditionalOnProperty(name = "security.enabled", havingValue="true")
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true, order = 1)
public class MethodSecurityConfig extends GlobalMethodSecurityConfiguration {
    @Bean
    public AccessCheck accessCheck(ApplicationProperties versionProperties, AppClientUserService appClientUserService) {
        return new AccessCheckImpl(versionProperties, appClientUserService);
    }

    @Bean
    public AccessCheckAppSource accessCheckAppSource(AppSourceRepository appSourceRepository,
                                                     AppClientUserRespository appClientUserRespository,
                                                     AppEndpointPrivRepository appEndpointPrivRepository) {
        return new AccessCheckAppSourceImpl(appSourceRepository, appClientUserRespository, appEndpointPrivRepository);
    }
    
    @Bean
    public AccessCheckEventRequestLog accessCheckEventRequestLog(AppClientUserRespository appClientUserRespository) {
        return new AccessCheckEventRequestLogImpl(appClientUserRespository);
    }
}
