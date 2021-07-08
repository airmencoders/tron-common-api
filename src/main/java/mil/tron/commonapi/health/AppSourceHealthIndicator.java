package mil.tron.commonapi.health;

import lombok.Getter;
import lombok.Setter;
import mil.tron.commonapi.entity.appsource.AppSource;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Class that manages the periodic health check for a given app source who
 * has elected to report health status to the Common API Dashboard.  Instances of
 * this class are created in the App Source service for App Sources that have health
 * reporting enabled.  See {@link mil.tron.commonapi.service.AppSourceServiceImpl#registerAppReporting(AppSource)}
 */

public class AppSourceHealthIndicator implements HealthIndicator {

    private static final long CONNECT_HEALTH_URL_TIMEOUT_SECS = 5L;
    private static final long READ_HEALTH_URL_TIMEOUT_SECS = 5L;
    private static final int UNINITIALIZED_HEALTH_STATUS_VAL = -2;
    private static final int UNKNOWN_HEALTH_STATUS_VAL = -1;
    private static final String STATUS_CODE_FIELD = "statusCode";

    /**
     * Handle to the ping task that hits the health url endpoint
     */
    private Future<?> pingTask;

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

    // ping rate in secs passed in from the app source service (defined in app props)
    //  default to 60 secs
    private long appSourcePingRateMillis = 60000L;

    /**
     * Health value we will continue to update.  We initialize this to
     * a status of {@link #UNINITIALIZED_HEALTH_STATUS_VAL} to indicate that the
     * health has not ran yet in case the actuator happens to be hit right away
     */
    private volatile Health health = Health
            .unknown()
            .withDetail(STATUS_CODE_FIELD, UNINITIALIZED_HEALTH_STATUS_VAL)
            .withDetail("error", "Health check has not run yet")
            .build();

    public AppSourceHealthIndicator(String name, String url) {
        this.url = url;
        this.name = name;
        this.init();
    }

    public AppSourceHealthIndicator(String name, String url, long appSourcePingRateMillis) {
        this.url = url;
        this.name = name;
        this.appSourcePingRateMillis = appSourcePingRateMillis;
        this.init();
    }

    private void init() {
        builder = new RestTemplateBuilder();
        healthSender = builder
                .setConnectTimeout(Duration.ofSeconds(CONNECT_HEALTH_URL_TIMEOUT_SECS))
                .setReadTimeout(Duration.ofSeconds(READ_HEALTH_URL_TIMEOUT_SECS))
                .build();

        ScheduledExecutorService executor = Executors
                .newScheduledThreadPool(Runtime
                        .getRuntime()
                        .availableProcessors());

        // start the health task, runs immediately, then again after delay value
        pingTask = executor.scheduleWithFixedDelay(this::doHealthPing, 0L, appSourcePingRateMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Called by Spring Actuator whenever we hit the /health actuator endpoint
     * @return Health status of the app source that owns this instance
     */
    @Override
    public Health health() {
        return this.health;
    }

    /**
     * Periodically ran task by the executor that hits the health url endpoint and
     * checks status / updates health
     */
    private void doHealthPing() {
        if (url == null || url.isBlank()) {
            this.health = Health.unknown().withDetail("reason", "No valid health check url available").build();
            return;
        }

        ResponseEntity<String> response = null;

        try {
            response = healthSender.getForEntity(url, String.class);  // this throws on a non successful response
            this.health = Health
                    .up()
                    .withDetail(STATUS_CODE_FIELD, response.getStatusCodeValue())
                    .build();
        }
        catch (HttpClientErrorException | HttpServerErrorException e) {
            if (e.getRawStatusCode() == 503) {
                this.health = Health
                        .outOfService()
                        .withDetail(STATUS_CODE_FIELD, e.getRawStatusCode())
                        .build();
            }
            else {
                this.health = Health
                        .down()
                        .withDetail(STATUS_CODE_FIELD, e.getRawStatusCode())
                        .build();
            }
        }
        catch (Exception e) {
            this.health = Health
                    .unknown()
                    .withDetail(STATUS_CODE_FIELD, UNKNOWN_HEALTH_STATUS_VAL)
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }

    /**
     * Called by App Source service when unregistering this app source for reporting health
     */
    public void cancelPing() {
        this.pingTask.cancel(false);
    }
}
