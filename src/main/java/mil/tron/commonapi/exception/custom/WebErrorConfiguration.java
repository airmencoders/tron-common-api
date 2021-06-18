package mil.tron.commonapi.exception.custom;

import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebErrorConfiguration {
    @Bean
    public ErrorAttributes errorAttributes() {
        return new TronCommonErrorAttributes();
    }
}
