package mil.tron.commonapi.service;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import mil.tron.commonapi.dto.metrics.AppClientCountMetricDto;
import mil.tron.commonapi.dto.metrics.AppSourceCountMetricDto;
import mil.tron.commonapi.dto.metrics.AppSourceMetricDto;
import mil.tron.commonapi.dto.metrics.CountMetricDto;
import mil.tron.commonapi.dto.metrics.EndpointCountMetricDto;
import mil.tron.commonapi.dto.metrics.EndpointMetricDto;
import mil.tron.commonapi.dto.metrics.MeterValueDto;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.CountMetric;
import mil.tron.commonapi.entity.MeterValue;
import mil.tron.commonapi.entity.appsource.AppEndpoint;
import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.MeterValueRepository;
import mil.tron.commonapi.repository.appsource.AppEndpointRepository;
import mil.tron.commonapi.repository.appsource.AppSourceRepository;

@Service
public class MetricServiceImpl implements MetricService {

  private MeterValueRepository meterValueRepo;

  private AppEndpointRepository appEndpointRepo;

  private AppClientUserRespository appClientRepo;

  private AppSourceRepository appSourceRepo;


  @Autowired
  public MetricServiceImpl(MeterValueRepository meterValueRepo, AppEndpointRepository appEndpointRepo, AppSourceRepository appSourceRepo, AppClientUserRespository appClientRepo) {
    this.meterValueRepo = meterValueRepo;
    this.appEndpointRepo = appEndpointRepo;
    this.appSourceRepo = appSourceRepo;
    this.appClientRepo = appClientRepo;
  }

  @Override
  public EndpointMetricDto getAllMetricsForEndpointDto(UUID id, Date startDate, Date endDate) {
    AppEndpoint appEndpoint = appEndpointRepo.findById(id).orElseThrow(() -> new RecordNotFoundException(String.format("App Endpoint with id %s not found", id.toString())));
    List<MeterValueDto> values = meterValueRepo.findAllByAppEndpointIdAndTimestampBetweenOrderByTimestampDesc(id, startDate, endDate).stream()
      .map(meterValue -> createMeterValueDto(meterValue))
      .collect(Collectors.toList());
    return this.createEndpointMetricDto(appEndpoint, values);
  }

  @Override
  public AppSourceMetricDto getMetricsForAppSource(UUID id, Date startDate, Date endDate) {
    AppSource appSource = getAppSource(id);
      List<EndpointMetricDto> endpointDtos = appSource.getAppEndpoints().stream()
        .map(endpoint -> createEndpointMetricDto(endpoint, meterValueRepo.findAllByAppEndpointIdAndTimestampBetweenOrderByTimestampDesc(endpoint.getId(), startDate, endDate).stream()
          .map(meterValue -> createMeterValueDto(meterValue))
          .collect(Collectors.toList())))
        .collect(Collectors.toList());
      return AppSourceMetricDto.builder()
        .endpoints(endpointDtos)
        .id(id)
        .name(appSource.getName())
        .build();
  }

  @Override
  public AppSourceCountMetricDto getCountOfMetricsForAppSource(UUID id, Date startDate, Date endDate) {
    AppSource appSource = getAppSource(id);
    List<CountMetricDto> endpointCountMetrics =  mapToCountMetricDtos(meterValueRepo.sumByEndpoint(id, startDate, endDate));
    List<CountMetricDto> appClientCountMetrics =  mapToCountMetricDtos(meterValueRepo.sumByAppClient(id, startDate, endDate));
      
    return AppSourceCountMetricDto.builder()
      .id(id)
      .name(appSource.getName())
      .endpoints(endpointCountMetrics)
      .appClients(appClientCountMetrics)
      .build();
  }

  @Override
  public EndpointCountMetricDto getCountOfMetricsForEndpoint(UUID id, String path, Date startDate, Date endDate) {
    AppSource appSource = getAppSource(id);
    AppEndpoint appEndpoint = appEndpointRepo.findByPathAndAppSource(path, appSource);
    if (appEndpoint == null) {
      throw new RecordNotFoundException(String.format("Endpoint with path %s not found on app source %s", path, appSource.getName()));
    }
    List<CountMetricDto> countMetrics = mapToCountMetricDtos(meterValueRepo.sumByAppSourceAndAppClientForEndpoint(id, path, startDate, endDate));
    
    return EndpointCountMetricDto.builder()
      .id(appEndpoint.getId())
      .path(path)
      .appClients(countMetrics)
      .appSource(appSource.getName())
      .build();
  }

  @Override
  public AppClientCountMetricDto getCountOfMetricsForAppClient(UUID id, String name, Date startDate, Date endDate) {
    AppSource appSource = getAppSource(id);
    AppClientUser appClient = appClientRepo.findByNameIgnoreCase(name).orElseThrow(() -> new RecordNotFoundException(String.format("App Client User with name %s not found", name)));
    List<CountMetricDto> countMetrics = mapToCountMetricDtos(meterValueRepo.sumByAppSourceAndEndpointForAppClient(id, name, startDate, endDate));
    
    return AppClientCountMetricDto.builder()
      .id(appClient.getId())
      .name(appClient.getName())
      .endpoints(countMetrics)
      .appSource(appSource.getName())
      .build();
  }
  
  private AppSource getAppSource(UUID id) throws RecordNotFoundException {
    return appSourceRepo.findById(id).orElseThrow(() -> new RecordNotFoundException(String.format("App Source with id %s not found", id.toString())));
  }

  private List<CountMetricDto> mapToCountMetricDtos(List<CountMetric> countMetrics) {
    return countMetrics.stream().map(ecm -> CountMetricDto.builder()
      .id(ecm.getId())
      .path(ecm.getName())
      .sum(ecm.getSum())
      .build())
    .collect(Collectors.toList());
  }

  @Override
  @Transactional
  public void publishToDatabase(List<List<Meter>> meters, Date now) {
      for(List<Meter> batch : meters) {            
          // We only care about timers, so everything else does nothing.
          batch.stream()
              .filter(m -> m.getId().getName().startsWith("gateway"))
              .forEach(meter -> meter.use(
                  g -> {}, // gauge
                  counter -> buildMeterValue(counter, now), // counter
                  t -> {}, // timer
                  d -> {}, // distribution summary
                  l -> {}, // long task timer
                  t -> {}, // time gauge
                  f -> {}, // function counter
                  f -> {}, // function timer
                  m -> {})); // generic/custom meter 
      }
  }

  private void buildMeterValue(Counter counter, Date date) {
      Meter.Id counterId = counter.getId();
      AppSource appSource = AppSource.builder()
          .id(UUID.fromString(counterId.getTag("AppSource")))
          .build();
      AppEndpoint appEndpoint = AppEndpoint.builder()
          .id(UUID.fromString(counterId.getTag("Endpoint")))
          .build();
      AppClientUser appClientUser = AppClientUser.builder()
          .id(UUID.fromString(counterId.getTag("AppClient")))
          .build();

      meterValueRepo.save(MeterValue.builder()
          .id(UUID.randomUUID())
          .timestamp(date)
          .value(counter.count())
          .metricName(counterId.getName())
          .appSource(appSource)
          .appEndpoint(appEndpoint)
          .appClientUser(appClientUser)
          .build());  
  }

  private EndpointMetricDto createEndpointMetricDto(AppEndpoint endpoint, List<MeterValueDto> values) {
    return EndpointMetricDto.builder()
      .id(endpoint.getId())
      .path(endpoint.getPath())
      .values(values)
      .build();
  }

  private MeterValueDto createMeterValueDto(MeterValue meterValue) {
    return MeterValueDto.builder()
      .appClient(meterValue.getAppClientUser().getName())
      .count(meterValue.getValue())
      .id(meterValue.getId())
      .timestamp(meterValue.getTimestamp())
      .metricName(meterValue.getMetricName())
      .build();
  }
}