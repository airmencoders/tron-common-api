package mil.tron.commonapi.service.kpi;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
import mil.tron.commonapi.entity.kpi.AppSourceMetricSummary;
import mil.tron.commonapi.entity.kpi.UserWithRequestCount;
import mil.tron.commonapi.entity.kpi.VisitorType;
import mil.tron.commonapi.repository.HttpLogsRepository;
import mil.tron.commonapi.repository.MeterValueRepository;
import mil.tron.commonapi.repository.appsource.AppSourceRepository;

@ExtendWith(MockitoExtension.class)
class KpiServiceImplTest {
	@Mock
	private HttpLogsRepository httpLogsRepo;
	
	@Mock
	private AppSourceRepository appSourceRepo;
	
	@Mock
	private MeterValueRepository meterValueRepo;
	
	@Mock
	private Clock systemUtcClock;

	@InjectMocks
	private KpiServiceImpl kpiService;
	
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
		Mockito.when(httpLogsRepo.getAverageLatencyForSuccessfulResponse(Mockito.any(Date.class), Mockito.any(Date.class))).thenReturn(Optional.of(33L));
		
		assertThat(kpiService.getAverageLatencyForSuccessResponse(Date.from(Instant.now()), Date.from(Instant.now().plus(1L, ChronoUnit.DAYS)))).isEqualTo(33L);
	}
	
	@Test
	void aggregateKpisTest() {
		List<UserWithRequestCount> userRequestCount = new ArrayList<>();
		UserWithRequestCount dashboardUserRequestCount = UserWithRequestCountDto.builder()
				.name("test@user.com")
				.requestCount(100L)
				.build();
		userRequestCount.add(dashboardUserRequestCount);
		
		UserWithRequestCount appClientUserRequestCount = UserWithRequestCountDto.builder()
				.name("App Client User")
				.requestCount(50L)
				.build();
		userRequestCount.add(appClientUserRequestCount);
		
		List<UniqueVisitorCountDto> uniqueVisitorCount = new ArrayList<>();
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
		
		Mockito.when(httpLogsRepo.getUsersWithRequestCount(Mockito.any(Date.class), Mockito.any(Date.class))).thenReturn(userRequestCount);
		
		Mockito.when(appSourceRepo.countByAvailableAsAppSourceTrue()).thenReturn(Optional.of(10L));
		Mockito.when(httpLogsRepo.getAverageLatencyForSuccessfulResponse(Mockito.any(Date.class), Mockito.any(Date.class))).thenReturn(Optional.of(33L));
		
		List<AppSourceMetricSummary> appSourceMetricSummary = new ArrayList<>();
		AppSourceMetricSummary appSourceMetric = AppSourceMetricSummaryDto.builder()
				.appClientName("guardianangel")
				.appSourceName("puckboard")
				.requestCount(1L)
				.build();
		appSourceMetricSummary.add(appSourceMetric);
		
		Mockito.when(meterValueRepo.getAllAppSourceMetricsSummary(Mockito.any(Date.class), Mockito.any(Date.class))).thenReturn(appSourceMetricSummary);
		
		Clock fixedClock = Clock.fixed(LocalDate.now(Clock.systemUTC()).atStartOfDay(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC"));
		Mockito.when(systemUtcClock.instant()).thenReturn(fixedClock.instant());
		Mockito.when(systemUtcClock.getZone()).thenReturn(fixedClock.getZone());

		var today = LocalDate.now(Clock.systemUTC());
		var tomorrow = LocalDate.now(Clock.systemUTC()).plusDays(1);
		KpiSummaryDto responseSummary = KpiSummaryDto.builder()
				.startDate(today)
				.endDate(tomorrow)
				.appClientToAppSourceRequestCount(1L)
				.appSourceCount(10L)
				.averageLatencyForSuccessfulRequests(33L)
				.uniqueVisitorCounts(uniqueVisitorCount)
				.build();
		
		assertThat(kpiService.aggregateKpis(today, tomorrow)).usingRecursiveComparison().ignoringFieldsOfTypes(UUID.class).isEqualTo(responseSummary);
	}
	
}
