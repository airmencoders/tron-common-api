package mil.tron.commonapi;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import mil.tron.commonapi.service.CustomMetricService;

public class CustomMeterRegistry extends StepMeterRegistry {

    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("gateway-metrics-publisher");
    private CustomRegistryConfig config;
    private CustomMetricService customMetricService;

	public CustomMeterRegistry(CustomRegistryConfig config, Clock clock, CustomMetricService customMetricService) {
		this(config, clock, DEFAULT_THREAD_FACTORY, customMetricService);
	}

    @Autowired
    public CustomMeterRegistry(CustomRegistryConfig config, Clock clock, ThreadFactory threadFactory, CustomMetricService customMetricService) {
		super(config, clock);
        this.config = config;
        this.customMetricService = customMetricService;
        start(threadFactory);
	}

    @Override
    public void start(ThreadFactory threadFactory) {
        super.start(threadFactory);
    }

    public CustomRegistryConfig getConfig() {
        return config;
    }

	@Override
	protected void publish() {
        System.out.println("~".repeat(150));
        System.out.println(getMeters().size());
        System.out.println("Is null: " + (customMetricService == null));
        for(Meter meter : getMeters()) {
            System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
            System.out.println(meter.getId());
            System.out.println(meter.getClass());
            for(Tag tag : meter.getId().getTags()) {
                System.out.println(String.format("%s: %s", tag.getKey(), tag.getValue()));
            }
            for(Measurement measurement : meter.measure()) {
                System.out.println(measurement.getValue());
                System.out.println(measurement.getStatistic());
            }
        }
		// getMeters().stream().forEach(meter -> System.out.println("Publishing " + meter.getId()));
        // partition into smaller chunks, because we might have a lot of stuff....
        this.customMetricService.publishToDatabase(MeterPartition.partition(this, config.batchSize()), clock, getBaseTimeUnit());
	}

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    } 
}