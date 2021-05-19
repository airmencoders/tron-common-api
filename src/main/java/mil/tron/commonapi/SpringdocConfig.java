package mil.tron.commonapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
			String.format("%s/subscriptions/**", apiPrefix),
			String.format("%s/flight/**", apiPrefix),
			String.format("%s/group/**", apiPrefix),
			String.format("%s/squadron/**", apiPrefix),
			String.format("%s/wing/**", apiPrefix),
			String.format("%s/userinfo/**", apiPrefix),
			String.format("%s/scratch/**", apiPrefix),
			String.format("%s/version/**", apiPrefix),
		};
    	
    	return GroupedOpenApi.builder().group("common-api").pathsToMatch(paths).build();
    }
	
    @Bean
    public GroupedOpenApi dashboardApi(@Value("${api-prefix.v1}") String apiPrefix) {
    	String[] paths = {
			String.format("%s/app-client/**", apiPrefix),
			String.format("%s/privilege/**", apiPrefix),
			String.format("%s/logfile/**", apiPrefix),
			String.format("%s/logs/**", apiPrefix),
			String.format("%s/dashboard-users/**", apiPrefix),
			String.format("%s/app-source/**", apiPrefix),
		};
    	return GroupedOpenApi.builder().group("dashboard-api").pathsToMatch(paths).build();
    }
    
    @Bean
    public GroupedOpenApi commonApiV2(@Value("${api-prefix.v2}") String apiPrefix) {
		String[] paths = {
				String.format("%s/person/**", apiPrefix),
				String.format("%s/organization/**", apiPrefix),
				String.format("%s/airman/**", apiPrefix),
				String.format("%s/subscriptions/**", apiPrefix),
				String.format("%s/flight/**", apiPrefix),
				String.format("%s/group/**", apiPrefix),
				String.format("%s/squadron/**", apiPrefix),
				String.format("%s/wing/**", apiPrefix),
				String.format("%s/userinfo/**", apiPrefix),
				String.format("%s/scratch/**", apiPrefix),
			};
    	
    	return GroupedOpenApi.builder().group("common-api-v2").pathsToMatch(paths).build();
    }
    
    @Bean
    public GroupedOpenApi dashboardApiV2(@Value("${api-prefix.v2}") String apiPrefix) {
    	String[] paths = {
			String.format("%s/app-client/**", apiPrefix),
			String.format("%s/privilege/**", apiPrefix),
			String.format("%s/logfile/**", apiPrefix),
			String.format("%s/dashboard-users/**", apiPrefix),
			String.format("%s/app-source/**", apiPrefix),
			String.format("%s/logs/**", apiPrefix),
		};
    	return GroupedOpenApi.builder().group("dashboard-api-v2").pathsToMatch(paths).build();
    }
}
