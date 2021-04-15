package mil.tron.commonapi;

import java.util.Date;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import mil.tron.commonapi.service.MetricService;

public class CustomMeterRegistry extends StepMeterRegistry {

    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("gateway-metrics-publisher");
    private CustomRegistryConfig config;
    private MetricService metricService;

	public CustomMeterRegistry(CustomRegistryConfig config, Clock clock, MetricService metricService) {
		this(config, clock, DEFAULT_THREAD_FACTORY, metricService);
	}

    @Autowired
    public CustomMeterRegistry(CustomRegistryConfig config, Clock clock, ThreadFactory threadFactory, MetricService metricService) {
		super(config, clock);
        this.config = config;
        this.metricService = metricService;
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
        // partition into smaller chunks, because we might have a lot of stuff....
        this.metricService.publishToDatabase(MeterPartition.partition(this, config.batchSize()), new Date());
	}

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    } 
}