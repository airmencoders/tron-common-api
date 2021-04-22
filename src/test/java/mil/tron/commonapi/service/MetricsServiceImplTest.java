package mil.tron.commonapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.annotation.RequestMethod;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import mil.tron.commonapi.dto.metrics.AppClientCountMetricDto;
import mil.tron.commonapi.dto.metrics.AppSourceCountMetricDto;
import mil.tron.commonapi.dto.metrics.AppSourceMetricDto;
import mil.tron.commonapi.dto.metrics.CountMetricDto;
import mil.tron.commonapi.dto.metrics.EndpointCountMetricDto;
import mil.tron.commonapi.dto.metrics.AppEndpointCountMetricDto;
import mil.tron.commonapi.dto.metrics.EndpointMetricDto;
import mil.tron.commonapi.dto.metrics.MeterValueDto;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.CountMetric;
import mil.tron.commonapi.entity.EndpointCountMetric;
import mil.tron.commonapi.entity.MeterValue;
import mil.tron.commonapi.entity.appsource.AppEndpoint;
import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.AppClientUserRespository;
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
	private AppClientUserRespository appClientUserRepo;

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
    private EndpointCountMetric countMetric1;
    private CountMetric countMetric2;
    private EndpointCountMetricDto countMetricDto1;
    private CountMetricDto countMetricDto2;
    private AppSourceCountMetricDto appSourceCountMetricDto;
    private AppEndpointCountMetricDto appEndpointCountMetricDto;
    private AppClientCountMetricDto appClientCountMetricDto;

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

        testEndpointMetricDto = EndpointMetricDto.endpointMetricBuilder()
                .id(appEndpoint.getId())
                .path(appEndpoint.getPath())
                .values(Arrays.asList(testMeterValueDto))
                .requestType(appEndpoint.getMethod().toString())
                .build();

        testAppSourceMetricDto = AppSourceMetricDto.builder()
                .id(appSource.getId())
                .endpoints(Arrays.asList(testEndpointMetricDto))
                .name(appSource.getName())
                .build();
        countMetric1 = new EndpointCountMetric() {
            private UUID id = UUID.randomUUID();
            private String name = "name";
            private Double sum = 2d;
            private RequestMethod method = RequestMethod.GET;
            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public Double getSum() {
                return sum;
            }            

            @Override
            public RequestMethod getMethod() {
                return method;
            }
        };

        countMetric2 = new CountMetric() {
            private UUID id = UUID.randomUUID();
            private String name = "name2";
            private Double sum = 4d;
            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public Double getSum() {
                return sum;
            }            
        };

        countMetricDto1 = EndpointCountMetricDto.endpointCountMetricBuilder()
                .id(countMetric1.getId())
                .path(countMetric1.getName())
                .sum(countMetric1.getSum())
                .method(countMetric1.getMethod().toString())
                .build();
        
        countMetricDto2 = CountMetricDto.builder()
                .id(countMetric2.getId())
                .path(countMetric2.getName())
                .sum(countMetric2.getSum())
                .build();

        appSourceCountMetricDto = AppSourceCountMetricDto.builder()
                .id(appSource.getId())
                .endpoints(Arrays.asList(countMetricDto1))
                .appClients(Arrays.asList(countMetricDto2))
                .name(appSource.getName())
                .build();

        appEndpointCountMetricDto = AppEndpointCountMetricDto.sumAppEndpointCountMetricBuilder()
                .id(appEndpoint.getId())
                .appClients(Arrays.asList(countMetricDto2))
                .appSource(appSource.getName())
                .path(appEndpoint.getPath())
                .requestType(appEndpoint.getMethod().toString())
                .build();

        appClientCountMetricDto = AppClientCountMetricDto.builder()
                .id(appClientUser.getId())
                .endpoints(Arrays.asList(countMetricDto1))
                .appSource(appSource.getName())
                .name(appClientUser.getName())
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
        testEndpointMetricDto = EndpointMetricDto.endpointMetricBuilder()
                .id(appEndpoint.getId())
                .path(appEndpoint.getPath())
                .values(Arrays.asList())
                .requestType(appEndpoint.getMethod().toString())
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
    void getMetricsForAppSourceTestWhereAppSourceDNETest() {
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

    @Test
    void getCountOfMetricsForAppSourceTest() {
        Mockito.when(appSourceRepo.findById(appSource.getId())).thenReturn(Optional.of(appSource));        
        Mockito.when(repository.sumByEndpoint(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(Arrays.asList(countMetric1));
        Mockito.when(repository.sumByAppClient(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(Arrays.asList(countMetric2));        
        AppSourceCountMetricDto result = metricService.getCountOfMetricsForAppSource(appSource.getId(), new Date(), new Date());
        assertThat(result).isEqualTo(appSourceCountMetricDto);
    }

    @Test
    void getCountOfMetricsForAppSourceTestWhereAppSourceDNETest() {
        Mockito.when(appSourceRepo.findById(appSource.getId())).thenReturn(Optional.empty());
        assertThatExceptionOfType(RecordNotFoundException.class).isThrownBy(() -> {
    		metricService.getCountOfMetricsForAppSource(appSource.getId(), new Date(), new Date());
    	});
    }

    @Test
    void getCountOfMetricsForAppSourceTestWhereNoMetricsExistTest() {
        appSource.getAppEndpoints().clear();
        appSourceCountMetricDto = AppSourceCountMetricDto.builder()
                .id(appSource.getId())
                .endpoints(new ArrayList<>())
                .appClients(new ArrayList<>())
                .name(appSource.getName())
                .build();

        Mockito.when(appSourceRepo.findById(appSource.getId())).thenReturn(Optional.of(appSource));
        AppSourceCountMetricDto result = metricService.getCountOfMetricsForAppSource(appSource.getId(), new Date(), new Date());
        assertThat(result).isEqualTo(appSourceCountMetricDto);
    }

    @Test
    void getCountOfMetricsForAppEndpointTest() {
        Mockito.when(appSourceRepo.findById(appSource.getId())).thenReturn(Optional.of(appSource));        
        Mockito.when(appEndpointRepo.findByPathAndAppSourceAndMethod(appEndpoint.getPath(), appSource, appEndpoint.getMethod())).thenReturn(appEndpoint);
        Mockito.when(repository.sumByAppSourceAndAppClientForEndpoint(Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(Arrays.asList(countMetric2));
        AppEndpointCountMetricDto result = metricService.getCountOfMetricsForEndpoint(appSource.getId(), appEndpoint.getPath(), appEndpoint.getMethod(), new Date(), new Date());
        assertThat(result).isEqualTo(appEndpointCountMetricDto);
    }

    @Test
    void getCountOfMetricsForAppEndpointTestWhereAppSourceDNETest() {
        Mockito.when(appSourceRepo.findById(appSource.getId())).thenReturn(Optional.empty());
        assertThatExceptionOfType(RecordNotFoundException.class).isThrownBy(() -> {
    		metricService.getCountOfMetricsForEndpoint(appSource.getId(), appEndpoint.getPath(), appEndpoint.getMethod(), new Date(), new Date());
    	});
    }

    @Test
    void getCountOfMetricsForAppEndpointTestWhereEndpointDNETest() {
        Mockito.when(appSourceRepo.findById(appSource.getId())).thenReturn(Optional.of(appSource)); 
        Mockito.when(appEndpointRepo.findByPathAndAppSourceAndMethod(appEndpoint.getPath(), appSource, appEndpoint.getMethod())).thenReturn(null);
        assertThatExceptionOfType(RecordNotFoundException.class).isThrownBy(() -> {
    		metricService.getCountOfMetricsForEndpoint(appSource.getId(), appEndpoint.getPath(), appEndpoint.getMethod(), new Date(), new Date());
    	});
    }

    @Test
    void getCountOfMetricsForAppEndpointTestWhereNoMetricsExistTest() {
        appSource.getAppEndpoints().clear();
        appEndpointCountMetricDto = AppEndpointCountMetricDto.sumAppEndpointCountMetricBuilder()
                .id(appEndpoint.getId())
                .appClients(new ArrayList<>())
                .path(appEndpoint.getPath())
                .appSource(appSource.getName())
                .requestType("GET")
                .build();

        Mockito.when(appSourceRepo.findById(appSource.getId())).thenReturn(Optional.of(appSource));
        Mockito.when(appEndpointRepo.findByPathAndAppSourceAndMethod(appEndpoint.getPath(), appSource, appEndpoint.getMethod())).thenReturn(appEndpoint);
        AppEndpointCountMetricDto result = metricService.getCountOfMetricsForEndpoint(appSource.getId(), appEndpoint.getPath(), appEndpoint.getMethod(), new Date(), new Date());
        assertThat(result).isEqualTo(appEndpointCountMetricDto);
    }

    @Test
    void getCountOfMetricsForAppClientUserTest() {
        Mockito.when(appSourceRepo.findById(appSource.getId())).thenReturn(Optional.of(appSource));        
        Mockito.when(appClientUserRepo.findByNameIgnoreCase(appClientUser.getName())).thenReturn(Optional.of(appClientUser));
        Mockito.when(repository.sumByAppSourceAndEndpointForAppClient(Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.any())).thenReturn(Arrays.asList(countMetric1));      
        AppClientCountMetricDto result = metricService.getCountOfMetricsForAppClient(appSource.getId(), appClientUser.getName(), new Date(), new Date());
        assertThat(result).isEqualTo(appClientCountMetricDto);
    }

    @Test
    void getCountOfMetricsForAppClientUserTestWhereAppSourceDNETest() {
        Mockito.when(appSourceRepo.findById(appSource.getId())).thenReturn(Optional.empty());
        assertThatExceptionOfType(RecordNotFoundException.class).isThrownBy(() -> {
    		metricService.getCountOfMetricsForAppClient(appSource.getId(), appClientUser.getName(), new Date(), new Date());
    	});
    }

    @Test
    void getCountOfMetricsForAppClientUserTestWhereAppClientUserDNETest() {
        Mockito.when(appSourceRepo.findById(appSource.getId())).thenReturn(Optional.of(appSource)); 
        Mockito.when(appClientUserRepo.findByNameIgnoreCase(appClientUser.getName())).thenReturn(Optional.empty());
        assertThatExceptionOfType(RecordNotFoundException.class).isThrownBy(() -> {
    		metricService.getCountOfMetricsForAppClient(appSource.getId(), appClientUser.getName(), new Date(), new Date());
    	});
    }

    @Test
    void getCountOfMetricsForAppClientUserTestWhereNoMetricsExistTest() {
        appSource.getAppEndpoints().clear();
        appClientCountMetricDto = AppClientCountMetricDto.builder()
                .id(appClientUser.getId())
                .endpoints(new ArrayList<>())
                .name(appClientUser.getName())
                .appSource(appSource.getName())
                .build();

        Mockito.when(appSourceRepo.findById(appSource.getId())).thenReturn(Optional.of(appSource)); 
        Mockito.when(appClientUserRepo.findByNameIgnoreCase(appClientUser.getName())).thenReturn(Optional.of(appClientUser));
        AppClientCountMetricDto result = metricService.getCountOfMetricsForAppClient(appSource.getId(), appClientUser.getName(), new Date(), new Date());
        assertThat(result).isEqualTo(appClientCountMetricDto);
    }

    @Test
    void publishToDataBaseTest() {
        Date date = new Date();
        MeterRegistry mockRegistry = new SimpleMeterRegistry();
        Counter counter = Counter.builder("gateway-counter.test")
        .tags(
            "AppSource", appSource.getId().toString(),
            "Endpoint", appEndpoint.getId().toString(),
            "AppClient", appClientUser.getId().toString())
        .register(mockRegistry);
        counter.increment();

        Meter.Id counterId = counter.getId();

        appSource = AppSource.builder()
            .id(UUID.fromString(counterId.getTag("AppSource")))
            .build();
        appEndpoint = AppEndpoint.builder()
            .id(UUID.fromString(counterId.getTag("Endpoint")))
            .build();
        appClientUser = AppClientUser.builder()
            .id(UUID.fromString(counterId.getTag("AppClient")))
            .build();


        Mockito.when(repository.save(Mockito.any(MeterValue.class))).thenReturn(null);

        ArgumentCaptor<MeterValue> savedCaptor = ArgumentCaptor.forClass(MeterValue.class);
        metricService.publishToDatabase(Arrays.asList(Arrays.asList(counter)), date);

        // Verify we saved, and capture what we tried to save
        verify(repository, times(1)).save(savedCaptor.capture());

        MeterValue result = savedCaptor.getValue();

        assertNotNull(result.getId());
        assertNotNull(result.getTimestamp());
        assertEquals(result.getAppClientUser().getId(), appClientUser.getId());
        assertEquals(result.getAppEndpoint().getId(), appEndpoint.getId());
        assertEquals(result.getAppSource().getId(), appSource.getId());
        assertEquals(result.getMetricName(), "gateway-counter.test");
        assertEquals(result.getValue(), 1d);
    }

    @Test
    void publishNonCounters() {
        MeterRegistry registry = new SimpleMeterRegistry();
        List<List<Meter>> meters = Arrays.asList(Arrays.asList(
            Gauge.builder("gateway-gauge.test", null, null).register(registry),
            Timer.builder("gateway-timer.test").register(registry),
            DistributionSummary.builder("gateway-summary.test").register(registry),
            LongTaskTimer.builder("gateway-long-task-timer.test").register(registry),
            TimeGauge.builder("gateway-timegauge.test", null, null, null).register(registry),
            FunctionCounter.builder("gateway-funtion-counter.test", null, null).register(registry),
            FunctionTimer.builder("gateway-function-timer.test", null, null, null, null).register(registry),
            Meter.builder("gateway-meter.test", null, null).register(registry)
        ));
        metricService.publishToDatabase(meters, new Date());

        // Verify we saved nothing
        verify(repository, times(0)).save(Mockito.any());
    }
}
