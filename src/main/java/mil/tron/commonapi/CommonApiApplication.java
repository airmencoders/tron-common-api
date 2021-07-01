package mil.tron.commonapi;

import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.repository.appsource.AppSourceRepository;
import mil.tron.commonapi.service.AppSourceService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

import java.util.List;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class CommonApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommonApiApplication.class, args);
    }
    
    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        CommonsRequestLoggingFilter loggingFilter = new CommonsRequestLoggingFilter();
        loggingFilter.setIncludeClientInfo(true);
        loggingFilter.setIncludeQueryString(true);
        loggingFilter.setIncludeHeaders(true);
        loggingFilter.setIncludePayload(true);
        loggingFilter.setMaxPayloadLength(64000);
        return loggingFilter;
    }

    /**
     * Launch the health check instances for each app source that's supposed to report status
     */
    @Bean
    public ApplicationRunner init(AppSourceService appSourceService, AppSourceRepository appSourceRepository) {

        return (args) -> {
            List<AppSource> appSources = appSourceRepository.findAll();
            for (AppSource appSource : appSources) {
                appSourceService.registerAppReporting(appSource);
            }
        };
    }
}
