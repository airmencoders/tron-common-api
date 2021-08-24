package mil.tron.commonapi.service.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import mil.tron.commonapi.dto.dashboard.AppSourceErrorResponseDto;
import mil.tron.commonapi.dto.dashboard.AppSourceErrorUsageDto;
import mil.tron.commonapi.dto.dashboard.AppSourceUsageDto;
import mil.tron.commonapi.dto.dashboard.AppSourceUsageResponseDto;
import mil.tron.commonapi.dto.dashboard.EntityAccessorDto;
import mil.tron.commonapi.dto.dashboard.EntityAccessorResponseDto;
import mil.tron.commonapi.dto.dashboard.ResponseDto;
import mil.tron.commonapi.entity.HttpLogEntry;
import mil.tron.commonapi.entity.dashboard.EntityAccessor;
import mil.tron.commonapi.repository.HttpLogsRepository;
import mil.tron.commonapi.service.utility.HttpLogsUtilService;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {
	@Mock
	private HttpLogsRepository httpLogsRepo;
	
	@Mock
	private HttpLogsUtilService httpLogsUtilService;
	
	@Mock
	private Clock systemUtcClock;
	
	@InjectMocks
	private DashboardServiceImpl dashboardService;
	
	@Test
	void getAppClientsAccessingOrgRecords_shouldThrow_onStartDateInFuture() {
		// Wednesday, July 21, 2021 0:00:00
		Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(1626825600000L), ZoneId.of("UTC"));
		Mockito.when(systemUtcClock.instant()).thenReturn(fixedClock.instant());
		
		// Wednesday, July 21, 2021 1:00:00
		Date startDateInFuture = new Date(1626829200000L);
		
		assertThatThrownBy(() -> dashboardService.getAppClientsAccessingOrgRecords(startDateInFuture, null))
			.hasMessageContaining("Start Date cannot be in the future");
	}
	
	@Test
	void getAppClientsAccessingOrgRecords_shouldThrow_onStartDateAfterEndDate() {
		// Wednesday, July 21, 2021 0:00:00
		Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(1626825600000L), ZoneId.of("UTC"));
		Mockito.when(systemUtcClock.instant()).thenReturn(fixedClock.instant());
		
		// Monday, July 19, 2021 0:00:00
		Date startDateAfterEndDate = new Date(1626652800000L);
		
		// Saturday, July 17, 2021 0:00:00
		Date endDate = new Date(1626480000000L);
		
		assertThatThrownBy(() -> dashboardService.getAppClientsAccessingOrgRecords(startDateAfterEndDate, endDate))
			.hasMessageContaining("Start Date must be before or equal to End Date");
	}
	
	@Test
	void getAppClientsAccessingOrgRecords_shouldReturn_onValidRequest() {
		// Wednesday, July 21, 2021 0:00:00
		Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(1626825600000L), ZoneId.of("UTC"));
		Mockito.when(systemUtcClock.instant()).thenReturn(fixedClock.instant());
		
		// Monday, July 19, 2021 0:00:00
		Date startDate = new Date(1626652800000L);
		
		// Tuesday, July 20, 2021 0:00:00
		Date endDate = new Date(1626739200000L);
		
		EntityAccessorDto entityAccessor = EntityAccessorDto.builder()
				.name("test accessor")
				.recordAccessCount(100L)
				.build();
		List<EntityAccessor> entityAccessors = List.of(entityAccessor);
		
		Mockito.when(httpLogsRepo.getUsersAccessingOrgRecords(Mockito.any(), Mockito.any())).thenReturn(entityAccessors);
		Mockito.when(httpLogsUtilService.isUsernameAnAppClient(entityAccessor.getName())).thenReturn(true);
		
		EntityAccessorResponseDto response = EntityAccessorResponseDto.builder()
				.startDate(startDate)
				.endDate(endDate)
				.entityAccessors(List.of(entityAccessor))
				.build();
		
		assertThat(dashboardService.getAppClientsAccessingOrgRecords(startDate, endDate)).isEqualTo(response);
	}
	
	@Test
	void getAppSourceUsage_shouldThrow_onStartDateInFuture() {
		// Wednesday, July 21, 2021 0:00:00
		Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(1626825600000L), ZoneId.of("UTC"));
		Mockito.when(systemUtcClock.instant()).thenReturn(fixedClock.instant());
		
		// Wednesday, July 21, 2021 1:00:00
		Date startDateInFuture = new Date(1626829200000L);
		
		assertThatThrownBy(() -> dashboardService.getAppSourceUsage(startDateInFuture, null, true, 2))
			.hasMessageContaining("Start Date cannot be in the future");
	}
	
	@Test
	void getAppSourceUsage_shouldThrow_onStartDateAfterEndDate() {
		// Wednesday, July 21, 2021 0:00:00
		Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(1626825600000L), ZoneId.of("UTC"));
		Mockito.when(systemUtcClock.instant()).thenReturn(fixedClock.instant());
		
		// Monday, July 19, 2021 0:00:00
		Date startDateAfterEndDate = new Date(1626652800000L);
		
		// Saturday, July 17, 2021 0:00:00
		Date endDate = new Date(1626480000000L);
		
		assertThatThrownBy(() -> dashboardService.getAppSourceUsage(startDateAfterEndDate, endDate, true, 2))
			.hasMessageContaining("Start Date must be before or equal to End Date");
	}
	
	@Test
	void getAppSourceUsage_shouldReturn_onValidRequest() {
		// Wednesday, July 21, 2021 0:00:00
		Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(1626825600000L), ZoneId.of("UTC"));
		Mockito.when(systemUtcClock.instant()).thenReturn(fixedClock.instant());
		
		// Monday, July 19, 2021 0:00:00
		Date startDate = new Date(1626652800000L);
		
		// Tuesday, July 20, 2021 0:00:00
		Date endDate = new Date(1626739200000L);
		
		HttpLogEntry entry1 = HttpLogEntry.builder()
				.requestedUrl("http://localhost:8888/api/v2/app/testApp/test_ep")
				.build();
		HttpLogEntry entry2 = HttpLogEntry.builder()
				.requestedUrl("http://localhost:8888/api/v2/app/testApp/test_ep_2")
				.build();
		HttpLogEntry entry3 = HttpLogEntry.builder()
				.requestedUrl("http://localhost:8888/api/v2/app/testApp2/ep")
				.build();
		
		Mockito.when(httpLogsRepo.getAppSourceUsage(Mockito.any(), Mockito.any())).thenReturn(List.of(entry1, entry2, entry3));
		
		AppSourceUsageDto testApp = AppSourceUsageDto.builder()
				.name("testApp")
				.incomingRequestCount(2L)
				.build();
		
		AppSourceUsageDto testApp2 = AppSourceUsageDto.builder()
				.name("testApp2")
				.incomingRequestCount(1L)
				.build();
		
		LinkedList<AppSourceUsageDto> appSourceUsageList = new LinkedList<>();
		appSourceUsageList.add(testApp);
		
		AppSourceUsageResponseDto response = AppSourceUsageResponseDto.builder()
				.startDate(startDate)
				.endDate(endDate)
				.appSourceUsage(appSourceUsageList)
				.build();
		
		// Test desc
		assertThat(dashboardService.getAppSourceUsage(startDate, endDate, true, 1)).isEqualTo(response);
		
		appSourceUsageList = new LinkedList<>();
		appSourceUsageList.add(testApp2);
		
		response = AppSourceUsageResponseDto.builder()
				.startDate(startDate)
				.endDate(endDate)
				.appSourceUsage(appSourceUsageList)
				.build();
		
		// Test asc
		assertThat(dashboardService.getAppSourceUsage(startDate, endDate, false, 1)).isEqualTo(response);
	}
	
	@Test
	void getAppSourceErrorUsage_shouldThrow_onStartDateInFuture() {
		// Wednesday, July 21, 2021 0:00:00
		Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(1626825600000L), ZoneId.of("UTC"));
		Mockito.when(systemUtcClock.instant()).thenReturn(fixedClock.instant());
		
		// Wednesday, July 21, 2021 1:00:00
		Date startDateInFuture = new Date(1626829200000L);
		
		assertThatThrownBy(() -> dashboardService.getAppSourceErrorUsage(startDateInFuture, null))
			.hasMessageContaining("Start Date cannot be in the future");
	}
	
	@Test
	void getAppSourceErrorUsage_shouldThrow_onStartDateAfterEndDate() {
		// Wednesday, July 21, 2021 0:00:00
		Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(1626825600000L), ZoneId.of("UTC"));
		Mockito.when(systemUtcClock.instant()).thenReturn(fixedClock.instant());
		
		// Monday, July 19, 2021 0:00:00
		Date startDateAfterEndDate = new Date(1626652800000L);
		
		// Saturday, July 17, 2021 0:00:00
		Date endDate = new Date(1626480000000L);
		
		assertThatThrownBy(() -> dashboardService.getAppSourceErrorUsage(startDateAfterEndDate, endDate))
			.hasMessageContaining("Start Date must be before or equal to End Date");
	}
	
	@Test
	void getAppSourceErrorUsage_shouldReturn_onValidRequest() {
		// Wednesday, July 21, 2021 0:00:00
		Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(1626825600000L), ZoneId.of("UTC"));
		Mockito.when(systemUtcClock.instant()).thenReturn(fixedClock.instant());
		
		// Monday, July 19, 2021 0:00:00
		Date startDate = new Date(1626652800000L);
		
		// Tuesday, July 20, 2021 0:00:00
		Date endDate = new Date(1626739200000L);
		
		HttpLogEntry entry1 = HttpLogEntry.builder()
				.requestedUrl("http://localhost:8888/api/v2/app/testApp/test_ep")
				.statusCode(503)
				.build();
		HttpLogEntry entry2 = HttpLogEntry.builder()
				.requestedUrl("http://localhost:8888/api/v2/app/testApp/test_ep_2")
				.statusCode(503)
				.build();
		HttpLogEntry entry3 = HttpLogEntry.builder()
				.requestedUrl("http://localhost:8888/api/v2/app/testApp/test_ep_2")
				.statusCode(400)
				.build();
		
		Mockito.when(httpLogsRepo.getAppSourceErrorUsage(Mockito.any(), Mockito.any())).thenReturn(List.of(entry1, entry2, entry3));
		
		AppSourceErrorUsageDto appSourceUsage = AppSourceErrorUsageDto.builder()
				.name("testApp")
				.totalErrorResponses(3L)
				.errorResponses(List.of(
							ResponseDto.builder()
								.count(2)
								.statusCode(503)
								.build(),
							ResponseDto.builder()
								.count(1)
								.statusCode(400)
								.build()
						))
				.build();
		
		AppSourceErrorResponseDto response = AppSourceErrorResponseDto.builder()
				.appSourceUsage(List.of(appSourceUsage))
				.startDate(startDate)
				.endDate(endDate)
				.build();
		
		assertThat(dashboardService.getAppSourceErrorUsage(startDate, endDate)).usingRecursiveComparison().ignoringCollectionOrder().isEqualTo(response);
	}
}
