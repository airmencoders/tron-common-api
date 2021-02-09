package mil.tron.commonapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import org.springdoc.core.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

@SpringBootApplication
@EnableAsync
public class CommonApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommonApiApplication.class, args);
    }
    
    @Bean
    public OpenAPI configOpenAPI(@Value("${api-version}") String version) {
    	return new OpenAPI()
    			.components(new Components())
    			.info(new Info()
    					.title("TRON Common API")
    					.version(version));
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
    
    @Bean
    public GroupedOpenApi commonApi(@Value("${api-prefix.v1}") String apiPrefix) {
    	String[] paths = {
			String.format("%s/person/**", apiPrefix),
			String.format("%s/organization/**", apiPrefix),
			String.format("%s/airman/**", apiPrefix),
		};
    	return GroupedOpenApi.builder().group("common-api").pathsToMatch(paths).build();
    }
    
    @Bean
    public GroupedOpenApi dashboardApi(@Value("${api-prefix.v1}") String apiPrefix) {
    	String[] paths = {
			String.format("%s/app-client/**", apiPrefix),
			String.format("%s/userinfo/**", apiPrefix)
		};
    	return GroupedOpenApi.builder().group("dashboard-api").pathsToMatch(paths).build();
    }
}
