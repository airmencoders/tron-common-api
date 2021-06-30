package mil.tron.commonapi.health;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

public class AppSourceHealthIndicator implements HealthIndicator {

    /**
     * Timeout parameters for hitting the app source's health url
     */
    private static final int CONNECT_TIMEOUT = 5;
    private static final int READ_TIMEOUT = 5;

    private static final String STATUS_CODE_FIELD = "statusCode";

    @Getter
    @Setter
    private String url;

    @Getter
    @Setter
    private String name;

    @Getter
    @Setter
    private RestTemplateBuilder builder;

    @Getter
    @Setter
    private RestTemplate healthSender;

    public AppSourceHealthIndicator(String name, String url) {
        this.url = url;
        this.name = name;
        builder = new RestTemplateBuilder();
        healthSender = builder
                .setConnectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT))
                .setReadTimeout(Duration.ofSeconds(READ_TIMEOUT))
                .build();

    }

    /**
     * This is run by Spring Actuator whenever we hit the /health endpoint
     * @return Health status of the app source that owns this instance
     */
    @Override
    public Health health() {
        if (url == null || url.isBlank()) {
            return Health.unknown().withDetail("reason", "No valid health check url available").build();
        }

        try {
            ResponseEntity<String> response = healthSender.getForEntity(url, String.class);
            if (response.getStatusCodeValue() >= 200 && response.getStatusCodeValue() < 300) {
                return Health
                    .up()
                    .withDetail(STATUS_CODE_FIELD, response.getStatusCodeValue())
                    .build();
            }
            else if (response.getStatusCodeValue() == 503) {
                return Health
                    .outOfService()
                    .withDetail(STATUS_CODE_FIELD, response.getStatusCodeValue())
                    .build();
            }
            else {
                return Health
                    .down()
                    .withDetail(STATUS_CODE_FIELD, response.getStatusCodeValue())
                    .build();
            }
        }
        catch (Exception e) {
            return Health
                    .unknown()
                    .withDetail(STATUS_CODE_FIELD, -1)
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
