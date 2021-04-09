package mil.tron.commonapi;

import java.util.Arrays;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import mil.tron.commonapi.service.CustomMetricService;

@Configuration
@ConditionalOnProperty(name = "metrics.save.enabled")
public class MetricsConfig {

    @Bean()
    public CompositeMeterRegistry compositeMeterRegistry(CustomMetricService customMetricService) {
        return new CompositeMeterRegistry(Clock.SYSTEM, Arrays.asList(
           new CustomMeterRegistry(CustomRegistryConfig.DEFAULT, Clock.SYSTEM, customMetricService) 
        ));
    }

    @Bean
    public MeterFilter meterFilter() {
        return MeterFilter.denyUnless((id) -> id.getName().startsWith("gateway"));
    }
}