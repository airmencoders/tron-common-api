package mil.tron.commonapi.service;

import java.util.Map;
import java.util.UUID;

import io.micrometer.core.instrument.Timer;
import mil.tron.commonapi.dto.metrics.AppSourceMetricDto;
import mil.tron.commonapi.dto.metrics.EndpointMetricDto;
import mil.tron.commonapi.entity.appsource.AppEndpoint;

public interface MetricService {
    public void increaseCount(AppEndpoint endpoint);
    public Map<UUID, Integer> getStatusMetric();
    public EndpointMetricDto getMetricForEndpoint(UUID id);
    public AppSourceMetricDto getMetricsForAppSource(UUID id);
}
