package mil.tron.commonapi.health;

import lombok.*;
import mil.tron.commonapi.service.pubsub.SubscriberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AppSourceHealthIndicator implements HealthIndicator {

    @Getter
    @Setter
    private String url;

    @Getter
    @Setter
    private String name;

    @Bean("healthRequester")
    public RestTemplate healthSender(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(5))
            .build();
    }

    private SubscriberService subService;

    @Autowired
    @Qualifier("healthRequester")
    private RestTemplate healthSender;

    @Override
    public Health health() {

        ResponseEntity<String> response = null;

        if (url == null || url.isBlank()) {
            return Health.unknown().withDetail("reason", "No valid health check url available").build();
        }

        try {
            response = healthSender.getForEntity(url, String.class);
            switch (response.getStatusCodeValue()) {
                case 200:
                case 201:
                    return Health.up().withDetail("statusCode", response.getStatusCodeValue()).build();
                default:
                    return Health.down().withDetail("statusCode", response.getStatusCodeValue()).build();
            }
        }
        catch (Exception e) {
            if (response != null) {
                return Health
                        .unknown()
                        .withDetail("statusCode", response.getStatusCodeValue())
                        .withDetail("response", response.getBody())
                        .withDetail("exception", e.getMessage())
                        .build();
            }
            else {
                return Health
                        .unknown()
                        .withDetail("statusCode", -1)
                        .withDetail("response", "")
                        .withDetail("exception", e.getMessage())
                        .build();
            }
        }
    }
}
