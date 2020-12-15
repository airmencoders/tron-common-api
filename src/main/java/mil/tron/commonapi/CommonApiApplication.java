package mil.tron.commonapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@SpringBootApplication
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

}
