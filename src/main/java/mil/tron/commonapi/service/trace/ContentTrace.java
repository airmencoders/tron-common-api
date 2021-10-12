package mil.tron.commonapi.service.trace;

import lombok.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Our custom trace for common (including request and response payloads)
 * Adapted from a design tutorial at https://developer.okta.com/blog/2019/07/17/monitoring-with-actuator
 */
@NoArgsConstructor
@Component
@Profile("production | development | staging | local")
public class ContentTrace {

    @Getter
    @Setter
    protected String requestBody;

    @Getter
    @Setter
    protected String responseBody;

    @Getter
    @Setter
    protected String errorMessage;

}
