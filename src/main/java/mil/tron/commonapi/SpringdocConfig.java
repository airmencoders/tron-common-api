package mil.tron.commonapi;

import org.springdoc.core.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class SpringdocConfig {
	@Bean
    public OpenAPI configOpenAPI(@Value("${api-version}") String version) {
    	return new OpenAPI()
    			.components(new Components())
    			.info(new Info()
    					.title("TRON Common API")
    					.version(version));
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
			String.format("%s/userinfo/**", apiPrefix),
			String.format("%s/list-request-headers", apiPrefix),
		};
    	return GroupedOpenApi.builder().group("dashboard-api").pathsToMatch(paths).build();
    }
}
