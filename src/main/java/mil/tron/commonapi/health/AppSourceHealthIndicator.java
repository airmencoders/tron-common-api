package mil.tron.commonapi.health;

import lombok.Getter;
import lombok.Setter;
import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.service.AppSourceService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
    private static final String LAST_UP_TIME = "Last Up Time";

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

    private Date lastUpTime;
    private UUID appSourceId;
    private AppSourceService appSourceService;

    /**
     * Health value we will continue to update.  We initialize this to
     * a status of {@link #UNINITIALIZED_HEALTH_STATUS_VAL} to indicate that the
     * health has not ran yet in case the actuator happens to be hit right away
     */
    private AtomicReference<Health> health = new AtomicReference<>(Health
            .unknown()
            .withDetail(STATUS_CODE_FIELD, UNINITIALIZED_HEALTH_STATUS_VAL)
            .withDetail("error", "Health check has not run yet")
            .build());

    public AppSourceHealthIndicator(String name, String url) {
        this.url = url;
        this.name = name;
        this.init();
    }

    public AppSourceHealthIndicator(String name, String url, long appSourcePingRateMillis, UUID appSourceId, AppSourceService appSourceService) {
        this.url = url;
        this.name = name;
        this.appSourcePingRateMillis = appSourcePingRateMillis;
        this.appSourceId = appSourceId;
        this.appSourceService = appSourceService;
        this.lastUpTime = this.appSourceService.getLastUpTime(this.appSourceId);
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
        pingTask = executor.scheduleWithFixedDelay(this::doHealthPing, 100L, appSourcePingRateMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Called by Spring Actuator whenever we hit the /health actuator endpoint
     * @return Health status of the app source that owns this instance
     */
    @Override
    public Health health() {
        return this.health.get();
    }

    private String getLastUpTime() {
        if (this.lastUpTime == null) {
            return "Unknown";
        }
        else {
            try {
                return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(this.lastUpTime);
            }
            catch (NullPointerException | IllegalArgumentException e) {
                return "Unknown";
            }
        }
    }

    /**
     * Periodically ran task by the executor that hits the health url endpoint and
     * checks status / updates health
     */
    private void doHealthPing() {
        if (url == null || url.isBlank()) {
            this.health.set(Health
                    .unknown()
                    .withDetail("reason", "No valid health check url available")
                    .build());
            return;
        }
        ResponseEntity<String> response = null;

        try {
            response = healthSender.getForEntity(url, String.class);  // this throws on a non successful response
            this.lastUpTime = new Date();
            this.appSourceService.updateLastUpTime(this.appSourceId, this.lastUpTime);
            this.health.set(Health
                    .up()
                    .withDetail(STATUS_CODE_FIELD, response.getStatusCodeValue())
                    .withDetail(LAST_UP_TIME, getLastUpTime())
                    .build());
        }
        catch (HttpClientErrorException | HttpServerErrorException e) {
            if (e.getRawStatusCode() == 503) {
                this.health.set(Health
                        .outOfService()
                        .withDetail(STATUS_CODE_FIELD, e.getRawStatusCode())
                        .withDetail(LAST_UP_TIME, getLastUpTime())
                        .build());
            }
            else {
                this.health.set(Health
                        .down()
                        .withDetail(STATUS_CODE_FIELD, e.getRawStatusCode())
                        .withDetail(LAST_UP_TIME, getLastUpTime())
                        .build());
            }
        }
        catch (Exception e) {
            this.health.set(Health
                    .unknown()
                    .withDetail(STATUS_CODE_FIELD, UNKNOWN_HEALTH_STATUS_VAL)
                    .withDetail(LAST_UP_TIME, getLastUpTime())
                    .withDetail("error", "Could not connect to health url")
                    .build());
        }
    }

    /**
     * Called by App Source service when unregistering this app source for reporting health
     */
    public void cancelPing() {
        this.pingTask.cancel(false);
    }
}
