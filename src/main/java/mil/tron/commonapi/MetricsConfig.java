package mil.tron.commonapi;

import java.time.Duration;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import mil.tron.commonapi.service.MetricService;

@Configuration
@ConditionalOnProperty(name = "metrics.save.enabled")
@PropertySource("classpath:application.properties")
public class MetricsConfig {

    @Bean()
    public CustomRegistryConfig customRegistryConfig(@Value("${metrics.stepsize:10}") int stepsize)  {
        return new CustomRegistryConfig(){
            @Override
            public Duration step() {
                return Duration.ofMinutes(stepsize);
            }

            @Override
            public String get(String key) {
                return null;
            }
        };
    }

    @Bean()
    public CompositeMeterRegistry compositeMeterRegistry(MetricService metricService, CustomRegistryConfig customRegistryConfig) {
        return new CompositeMeterRegistry(Clock.SYSTEM, Arrays.asList(
           new CustomMeterRegistry(customRegistryConfig, Clock.SYSTEM, metricService) 
        ));
    }

    @Bean
    public MeterFilter meterFilter() {
        return MeterFilter.denyUnless(id -> id.getName().startsWith("gateway"));
    }
}