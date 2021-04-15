package mil.tron.commonapi.service;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import io.micrometer.core.instrument.Meter;
import mil.tron.commonapi.dto.metrics.AppSourceMetricDto;
import mil.tron.commonapi.dto.metrics.EndpointMetricDto;

public interface MetricService {
    public EndpointMetricDto getAllMetricsForEndpointDto(UUID id, Date startDate, Date endDate);
    public AppSourceMetricDto getMetricsForAppSource(UUID id, Date startDate, Date endDate);
    public void publishToDatabase(List<List<Meter>> meters, Date now);
}
