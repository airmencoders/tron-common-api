package mil.tron.commonapi.service;

import java.util.Date;
import java.util.UUID;

import mil.tron.commonapi.dto.metrics.AppSourceMetricDto;
import mil.tron.commonapi.dto.metrics.EndpointMetricDto;

public interface MetricService {
    public EndpointMetricDto getAllMetricsForEndpointDto(UUID id, Date startDate, Date endDate);
    public AppSourceMetricDto getMetricsForAppSource(UUID id, Date startDate, Date endDate);
}
