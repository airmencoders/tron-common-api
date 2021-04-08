package mil.tron.commonapi;

import java.util.Arrays;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import mil.tron.commonapi.service.CustomMetricService;

@Configuration
public class MetricsConfig {

	// @Bean
	// public CustomRegistryConfig customRegistryConfig() {
	// 	return CustomRegistryConfig.DEFAULT;
	// }

    // This is the bean that is not working. I do not know why.
	// @Bean()
	// public CustomMeterRegistry customMeterRegistry(CustomRegistryConfig config, Clock clock) {
    //     CustomMeterRegistry cmr = new CustomMeterRegistry(config, clock);
    //     // cmr.config().meterFilter(MeterFilter.denyUnless((id) -> id.getName().startsWith("gateway")));
    //     return cmr;
	// }

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