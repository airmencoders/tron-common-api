package mil.tron.commonapi.service;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;

public interface CustomMetricService {
    public void publishToDatabase(List<List<Meter>> meters, Clock clock, TimeUnit baseTimeUnit);
}
