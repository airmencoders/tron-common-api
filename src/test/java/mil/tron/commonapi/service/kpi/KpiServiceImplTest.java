package mil.tron.commonapi.service.kpi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import mil.tron.commonapi.dto.kpi.AppSourceMetricSummaryDto;
import mil.tron.commonapi.dto.kpi.KpiSummaryDto;
import mil.tron.commonapi.dto.kpi.UniqueVisitorCountDto;
import mil.tron.commonapi.dto.kpi.UserWithRequestCountDto;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.entity.kpi.AppSourceMetricSummary;
import mil.tron.commonapi.entity.kpi.KpiSummary;
import mil.tron.commonapi.entity.kpi.UserWithRequestCount;
import mil.tron.commonapi.entity.kpi.VisitorType;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.repository.HttpLogsRepository;
import mil.tron.commonapi.repository.MeterValueRepository;
import mil.tron.commonapi.repository.appsource.AppSourceRepository;
import mil.tron.commonapi.repository.kpi.KpiRepository;
import mil.tron.commonapi.service.utility.HttpLogsUtilService;

@ExtendWith(MockitoExtension.class)
class KpiServiceImplTest {
	private static final DtoMapper MODEL_MAPPER = new DtoMapper();
	
	@Mock
	private HttpLogsRepository httpLogsRepo;
	
	@Mock
	private AppSourceRepository appSourceRepo;
	
	@Mock
	private MeterValueRepository meterValueRepo;
	
	@Mock
	private HttpLogsUtilService httpLogsUtilService;
	
	@Mock 
	private KpiRepository kpiRepo;
	
	@Mock
	private Clock systemUtcClock;

	@InjectMocks
	private KpiServiceImpl kpiService;
	
	private UserWithRequestCount dashboardUserRequestCount;
	private UserWithRequestCount appClientUserRequestCount;
	private List<UserWithRequestCount> userRequestCount;
	private List<UniqueVisitorCountDto> uniqueVisitorCount;
	private KpiSummaryDto dto;

	
	@BeforeEach
	void setup() {
		userRequestCount = new ArrayList<>();
		dashboardUserRequestCount = UserWithRequestCountDto.builder()
				.name("test@user.com")
				.requestCount(100L)
				.build();
		userRequestCount.add(dashboardUserRequestCount);
		
		appClientUserRequestCount = UserWithRequestCountDto.builder()
				.name("App Client User")
				.requestCount(50L)
				.build();
		userRequestCount.add(appClientUserRequestCount);
		
		uniqueVisitorCount = new ArrayList<>();
		uniqueVisitorCount.add(UniqueVisitorCountDto.builder()
					.visitorType(VisitorType.DASHBOARD_USER)
					.uniqueCount(1L)
					.requestCount(dashboardUserRequestCount.getRequestCount())
					.build());
		
		uniqueVisitorCount.add(UniqueVisitorCountDto.builder()
				.visitorType(VisitorType.APP_CLIENT)
				.uniqueCount(1L)
				.requestCount(appClientUserRequestCount.getRequestCount())
				.build());
		

		dto = KpiSummaryDto.builder()
				.appClientToAppSourceRequestCount(1L)
				.appSourceCount(10L)
				.averageLatencyForSuccessfulRequests(33d)
				.uniqueVisitorCounts(uniqueVisitorCount)
				.build();
	}
	
	@Test
	void getUsersWithRequestCountTest() {
		List<UserWithRequestCount> userRequestCount = new ArrayList<>();
		UserWithRequestCount userRequest = UserWithRequestCountDto.builder()
				.name("Test User")
				.requestCount(100L)
				.build();
		userRequestCount.add(userRequest);
		
		Mockito.when(httpLogsRepo.getUsersWithRequestCount(Mockito.any(Date.class), Mockito.any(Date.class))).thenReturn(userRequestCount);
		
		assertThat(kpiService.getUsersWithRequestCount(Date.from(Instant.now()), Date.from(Instant.now().plus(1L, ChronoUnit.DAYS)))).containsAll(userRequestCount);
	}
	
	@Test
	void getAppSourceCountTest() {
		Mockito.when(appSourceRepo.countByAvailableAsAppSourceTrue()).thenReturn(Optional.of(10L));
		
		assertThat(kpiService.getAppSourceCount()).isEqualTo(10L);
	}
	
	@Test
	void getAverageLatencyForSuccessfulResponseTest() {
		Mockito.when(httpLogsRepo.getAverageLatencyForSuccessfulResponse(Mockito.any(Date.class), Mockito.any(Date.class))).thenReturn(Optional.of(33d));
		
		assertThat(kpiService.getAverageLatencyForSuccessResponse(Date.from(Instant.now()), Date.from(Instant.now().plus(1L, ChronoUnit.DAYS)))).isEqualTo(33L);
	}
	
	@Test
	void aggregateKpisTest() {
		// Wednesday, July 21, 2021 0:00:00
		Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(1626825600000L), ZoneId.of("UTC"));
		Mockito.when(systemUtcClock.instant()).thenReturn(fixedClock.instant());

		// Thursday, July 15, 2021 0:00:00
		var start = Instant.ofEpochMilli(1626307200000L);
		var startAsDate = Date.from(start);
		
		// Wednesday, July 21, 2021 23:59:59
		// Set to 23:59:59 due to service doing this for inclusivity of the day
		var end = Instant.ofEpochMilli(1626911999000L);
		var endAsDate = Date.from(end);
		
		dto.setStartDate(startAsDate);
		dto.setEndDate(endAsDate);
		
		Mockito.when(httpLogsUtilService.getDateAtStartOfDay(Mockito.any())).thenReturn(startAsDate);
		Mockito.when(httpLogsUtilService.getDateAtEndOfDay(Mockito.any())).thenReturn(endAsDate);
		
		Mockito.when(httpLogsRepo.getUsersWithRequestCount(Mockito.any(Date.class), Mockito.any(Date.class))).thenReturn(userRequestCount);
		Mockito.when(httpLogsUtilService.isUsernameAnAppClient(dashboardUserRequestCount.getName())).thenReturn(false);
		Mockito.when(httpLogsUtilService.isUsernameAnAppClient(appClientUserRequestCount.getName())).thenReturn(true);
		
		Mockito.when(appSourceRepo.countByAvailableAsAppSourceTrue()).thenReturn(Optional.of(dto.getAppSourceCount()));
		Mockito.when(httpLogsRepo.getAverageLatencyForSuccessfulResponse(Mockito.any(Date.class), Mockito.any(Date.class))).thenReturn(Optional.of(dto.getAverageLatencyForSuccessfulRequests()));
		
		List<AppSourceMetricSummary> appSourceMetricSummary = new ArrayList<>();
		AppSourceMetricSummary appSourceMetric = AppSourceMetricSummaryDto.builder()
				.appClientName("guardianangel")
				.appSourceName("puckboard")
				.requestCount(1L)
				.build();
		appSourceMetricSummary.add(appSourceMetric);
		
		Mockito.when(meterValueRepo.getAllAppSourceMetricsSummary(Mockito.any(Date.class), Mockito.any(Date.class))).thenReturn(appSourceMetricSummary);
		
		assertThat(kpiService.aggregateKpis(startAsDate, endAsDate)).usingRecursiveComparison().ignoringFieldsOfTypes(UUID.class).isEqualTo(dto);
	}
	
	@Test
	void aggregateKpis_shouldThrow_whenStartDateInFuture() {
		// Tuesday, July 27, 2021 0:00:00
		Instant currentDate = Instant.ofEpochMilli(1627344000000L);
		
		Clock fixedClock = Clock.fixed(currentDate, ZoneId.of("UTC"));
		Mockito.when(systemUtcClock.instant()).thenReturn(fixedClock.instant());
		
		// Wednesday, August 4, 2021 0:00:00
		Instant dateInFuture = Instant.ofEpochMilli(1628035200000L);
		
		assertThatThrownBy(() -> {
			kpiService.aggregateKpis(Date.from(dateInFuture), null);
		})
		.isInstanceOf(BadRequestException.class)
		.hasMessageContaining("Start Date cannot be in the future");
	}
	
	@Test
	void aggregateKpis_shouldThrow_whenStartDateIsGreaterThanEndDate() {
		// Monday, July 26, 2021 0:00:00
		Instant currentDate = Instant.ofEpochMilli(1627257600000L);
		
		Clock fixedClock = Clock.fixed(currentDate, ZoneId.of("UTC"));
		Mockito.when(systemUtcClock.instant()).thenReturn(fixedClock.instant());
		
		// Thursday, July 22, 2021 0:00:00
		Instant startDate = Instant.ofEpochMilli(1626912000000L);
		
		// Wednesday, July 21, 2021 0:00:00
		Instant endDate = Instant.ofEpochMilli(1626825600000L);
		
		assertThatThrownBy(() -> {
			kpiService.aggregateKpis(Date.from(startDate), Date.from(endDate));
		})
		.isInstanceOf(BadRequestException.class)
		.hasMessageContaining("Start date must be before or equal to End Date");
	}
	
	@Test
	void getKpisRangeOnStartDateBetween_shouldThrow_whenStartDateInFuture() {
		// Tuesday, July 27, 2021 0:00:00
		Instant currentDate = Instant.ofEpochMilli(1627344000000L);
		
		Clock fixedClock = Clock.fixed(currentDate, ZoneId.of("UTC"));
		Mockito.when(systemUtcClock.instant()).thenReturn(fixedClock.instant());
		Mockito.when(systemUtcClock.getZone()).thenReturn(fixedClock.getZone());
		
		// Wednesday, August 4, 2021 0:00:00
		Instant dateInFuture = Instant.ofEpochMilli(1628035200000L);
		
		assertThatThrownBy(() -> {
			kpiService.getKpisRangeOnStartDateBetween(Date.from(dateInFuture), null);
		})
		.isInstanceOf(BadRequestException.class)
		.hasMessageContaining("Start Date cannot be set within the current week or the future");
	}
	
	@Test
	void getKpisRangeOnStartDateBetween_shouldThrow_whenStartDateIsWithinCurrentWeek() {
		// Monday, July 26, 2021 0:00:00
		Instant currentDate = Instant.ofEpochMilli(1627257600000L);
		
		Clock fixedClock = Clock.fixed(currentDate, ZoneId.of("UTC"));
		Mockito.when(systemUtcClock.instant()).thenReturn(fixedClock.instant());
		Mockito.when(systemUtcClock.getZone()).thenReturn(fixedClock.getZone());
		
		// Monday, August 2, 2021 0:00:00
		Instant dateThatWillBeWithinCurrentWeek = Instant.ofEpochMilli(1627862400000L);
		
		assertThatThrownBy(() -> {
			kpiService.getKpisRangeOnStartDateBetween(Date.from(dateThatWillBeWithinCurrentWeek), null);
		})
		.isInstanceOf(BadRequestException.class)
		.hasMessageContaining("Start Date cannot be set within the current week or the future");
	}
	
	@Test
	void getKpisRangeOnStartDateBetween_shouldThrow_whenStartDateIsGreaterThanEndDate() {
		// Wednesday, August 11, 2021 0:00:00
		Instant currentDate = Instant.ofEpochMilli(1628640000000L);
		
		Clock fixedClock = Clock.fixed(currentDate, ZoneId.of("UTC"));
		Mockito.when(systemUtcClock.instant()).thenReturn(fixedClock.instant());
		Mockito.when(systemUtcClock.getZone()).thenReturn(fixedClock.getZone());
		
		// Wednesday, August 4, 2021 0:00:00
		Instant startDate = Instant.ofEpochMilli(1628035200000L);
		
		// Wednesday, July 21, 2021 0:00:00
		Instant endDate = Instant.ofEpochMilli(1626825600000L);
		
		assertThatThrownBy(() -> {
			kpiService.getKpisRangeOnStartDateBetween(Date.from(startDate), Date.from(endDate));
		})
		.isInstanceOf(BadRequestException.class)
		.hasMessageContaining("Start date must be before or equal to End Date");
	}
	
	@Test
	void getKpisRangeOnStartDateBetweenTest() {
		// Thursday, July 15, 2021 0:00:00
		var start = Instant.ofEpochMilli(1626307200000L);
		var startAsDate = Date.from(start);
		
		// Wednesday, July 21, 2021 23:59:59
		var end = Instant.ofEpochMilli(1626911999000L);
		var endAsDate = Date.from(end);
		
		dto.setStartDate(startAsDate);
		dto.setEndDate(endAsDate);
		
		KpiSummary entity = MODEL_MAPPER.map(dto, KpiSummary.class);
		
		List<KpiSummary> response = new ArrayList<>();
		response.add(entity);
		
		List<KpiSummaryDto> responseAsDto = new ArrayList<>();
		responseAsDto.add(dto);
		
		// Wednesday, July 21, 2021 0:00:00
		Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(1626825600000L), ZoneId.of("UTC"));
		Mockito.when(systemUtcClock.instant()).thenReturn(fixedClock.instant());
		Mockito.when(systemUtcClock.getZone()).thenReturn(fixedClock.getZone());
		
		Mockito.when(kpiRepo.findByStartDateBetween(Mockito.any(Date.class), Mockito.nullable(Date.class))).thenReturn(response);
		
		assertThat(kpiService.getKpisRangeOnStartDateBetween(startAsDate, endAsDate)).usingRecursiveComparison().ignoringFieldsOfTypes(UUID.class).isEqualTo(responseAsDto);
	}
	
	@Test
	void saveAggregatedKpisTest() {
		// Thursday, July 15, 2021 0:00:00
		var start = Instant.ofEpochMilli(1626307200000L);
		var startAsDate = Date.from(start);
		
		// Wednesday, July 21, 2021 23:59:59
		var end = Instant.ofEpochMilli(1626911999000L);
		var endAsDate = Date.from(end);
		
		dto.setStartDate(startAsDate);
		dto.setEndDate(endAsDate);
		
		KpiSummary entity = MODEL_MAPPER.map(dto, KpiSummary.class);
		
		Mockito.when(kpiRepo.save(Mockito.any(KpiSummary.class))).thenReturn(entity);
		
		assertThat(kpiService.saveAggregatedKpis(dto)).usingRecursiveComparison().ignoringFieldsOfTypes(UUID.class).isEqualTo(dto);
	}
}
