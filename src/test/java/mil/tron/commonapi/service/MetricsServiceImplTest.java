package mil.tron.commonapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.annotation.RequestMethod;

import mil.tron.commonapi.dto.metrics.AppSourceMetricDto;
import mil.tron.commonapi.dto.metrics.EndpointMetricDto;
import mil.tron.commonapi.dto.metrics.MeterValueDto;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.MeterValue;
import mil.tron.commonapi.entity.appsource.AppEndpoint;
import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.MeterValueRepository;
import mil.tron.commonapi.repository.appsource.AppEndpointRepository;
import mil.tron.commonapi.repository.appsource.AppSourceRepository;

@ExtendWith(MockitoExtension.class)
class MetricsServiceImplTest {
    @Mock
	private MeterValueRepository repository;

	@Mock
	private AppEndpointRepository appEndpointRepo;

	@Mock
	private AppSourceRepository appSourceRepo;
	
	@InjectMocks
	private MetricServiceImpl metricService;

	private MeterValue testMeterValue;
	private MeterValueDto testMeterValueDto;
    private AppEndpoint appEndpoint;
    private EndpointMetricDto testEndpointMetricDto;
    private AppClientUser appClientUser;
    private AppSource appSource;
    private AppSourceMetricDto testAppSourceMetricDto;

	@BeforeEach
	void beforeEachSetup() {
        appClientUser = AppClientUser.builder()
                .id(UUID.randomUUID())
                .name("appclient")
                .build();

        appSource = AppSource.builder()
                .id(UUID.randomUUID())
                .appSourcePath("appsourcepath")
                .name("appsourcename")
                .build();

		testMeterValue = MeterValue.builder()
                .metricName("testMetric")
                .timestamp(new Date())
                .value(1d)
                .id(UUID.randomUUID())
                .appClientUser(appClientUser)
                .build();
        
        testMeterValueDto = MeterValueDto.builder()
                .id(testMeterValue.getId())
                .metricName(testMeterValue.getMetricName())
                .timestamp(testMeterValue.getTimestamp())
                .count(testMeterValue.getValue())
                .build();
        
        appEndpoint = AppEndpoint.builder()
                .id(UUID.randomUUID())
                .method(RequestMethod.GET)
                .path("path")
                .build();
        
        appSource.getAppEndpoints().add(appEndpoint);

        testEndpointMetricDto = EndpointMetricDto.builder()
                .id(appEndpoint.getId())
                .path(appEndpoint.getPath())
                .values(Arrays.asList(testMeterValueDto))
                .build();

        testAppSourceMetricDto = AppSourceMetricDto.builder()
                .id(appSource.getId())
                .endpoints(Arrays.asList(testEndpointMetricDto))
                .name(appSource.getName())
                .build();
	}

    @Test
    void getAllMetricsForEndpointDtoTest() {
        Mockito.when(appEndpointRepo.findById(appEndpoint.getId())).thenReturn(Optional.of(appEndpoint));
        Mockito.when(repository.findAllByAppEndpointIdAndTimestampBetweenOrderByTimestampDesc(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(Arrays.asList(testMeterValue));
        EndpointMetricDto result = metricService.getAllMetricsForEndpointDto(appEndpoint.getId(), new Date(), new Date());
        assertThat(result).isEqualTo(testEndpointMetricDto);
    }

    @Test
    void getAllMetricsForEndpointDtoWhereEndpointDNETest() {
        Mockito.when(appEndpointRepo.findById(appEndpoint.getId())).thenReturn(Optional.empty());
        assertThatExceptionOfType(RecordNotFoundException.class).isThrownBy(() -> {
    		metricService.getAllMetricsForEndpointDto(appEndpoint.getId(), new Date(), new Date());
    	});
    }

    @Test
    void getAllMetricsForEndpointDtoWhereNoMetricsExistTest() {
        testEndpointMetricDto = EndpointMetricDto.builder()
                .id(appEndpoint.getId())
                .path(appEndpoint.getPath())
                .values(Arrays.asList())
                .build();

        Mockito.when(appEndpointRepo.findById(appEndpoint.getId())).thenReturn(Optional.of(appEndpoint));
        Mockito.when(repository.findAllByAppEndpointIdAndTimestampBetweenOrderByTimestampDesc(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(new ArrayList<>());
        EndpointMetricDto result = metricService.getAllMetricsForEndpointDto(appEndpoint.getId(), new Date(), new Date());
        assertThat(result).isEqualTo(testEndpointMetricDto);
    }

    @Test
    void getMetricsForAppSourceTest() {
        Mockito.when(appSourceRepo.findById(appSource.getId())).thenReturn(Optional.of(appSource));
        Mockito.when(repository.findAllByAppEndpointIdAndTimestampBetweenOrderByTimestampDesc(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(Arrays.asList(testMeterValue));
        AppSourceMetricDto result = metricService.getMetricsForAppSource(appSource.getId(), new Date(), new Date());
        assertThat(result).isEqualTo(testAppSourceMetricDto);
    }

    @Test
    void getMetricsForAppSourceTestWhereEndpointDNETest() {
        Mockito.when(appSourceRepo.findById(appSource.getId())).thenReturn(Optional.empty());
        assertThatExceptionOfType(RecordNotFoundException.class).isThrownBy(() -> {
    		metricService.getMetricsForAppSource(appSource.getId(), new Date(), new Date());
    	});
    }

    @Test
    void getMetricsForAppSourceTestWhereNoMetricsExistTest() {
        appSource.getAppEndpoints().clear();
        testAppSourceMetricDto = AppSourceMetricDto.builder()
                .id(appSource.getId())
                .endpoints(new ArrayList<>())
                .name(appSource.getName())
                .build();

        Mockito.when(appSourceRepo.findById(appSource.getId())).thenReturn(Optional.of(appSource));
        AppSourceMetricDto result = metricService.getMetricsForAppSource(appSource.getId(), new Date(), new Date());
        assertThat(result).isEqualTo(testAppSourceMetricDto);
    }
}
