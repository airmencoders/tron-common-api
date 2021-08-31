package mil.tron.commonapi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import mil.tron.commonapi.dto.kpi.ServiceMetricDto;
import mil.tron.commonapi.entity.HttpLogEntry;

@SpringBootTest
class HttpLogsRepositoryTest {
	private static final String BASE_URL = "http://localhost/api/v2";
	private static final String APP_GATEWAY_PREFIX = "/app";
	private static final String APP_GATEWAY_BASE_URL = BASE_URL + APP_GATEWAY_PREFIX;
	
	@Autowired
	private HttpLogsRepository repo;
	
	private List<HttpLogEntry> entriesOutsideOfDateRange;
	private List<HttpLogEntry> appSourceEntriesSuccess;
	private List<HttpLogEntry> appSourceEntriesFail;
	private List<HttpLogEntry> personEntries;
	private List<HttpLogEntry> orgEntries;
	private List<HttpLogEntry> otherEntries;
	private List<HttpLogEntry> allEntries;
	
	private Date logDate;
	private Date startDate;
	private Date endDate;
	private Date logDateOutsideOfDateRange;
	
	@BeforeEach
	void setup() {
		repo.deleteAll();
		
		// Sunday, August 29, 2021 1:00:00
		logDate = new Date(1630198800000L);
		
		// Saturday, August 28, 2021 1:00:00
		startDate = new Date(1630112400000L);
		
		// Monday, August 30, 2021 1:00:00
		endDate = new Date(1630285200000L);
		
		// Saturday, July 31, 2021 0:00:00
		logDateOutsideOfDateRange = new Date(1627689600000L);
		
		entriesOutsideOfDateRange = List.of(
				HttpLogEntry.builder()
					.id(UUID.randomUUID())
					.statusCode(200)
					.requestTimestamp(logDateOutsideOfDateRange)
					.requestedUrl(APP_GATEWAY_BASE_URL + "/test-app-source/person")
					.timeTakenMs(100L)
					.build(),
				HttpLogEntry.builder()
					.id(UUID.randomUUID())
					.statusCode(200)
					.requestTimestamp(logDateOutsideOfDateRange)
					.requestedUrl(BASE_URL + "/person/self")
					.timeTakenMs(1000L)
					.build(),
				HttpLogEntry.builder()
					.id(UUID.randomUUID())
					.statusCode(200)
					.requestTimestamp(logDateOutsideOfDateRange)
					.requestedUrl(BASE_URL + "/organization/person")
					.timeTakenMs(500L)
					.build(),
				HttpLogEntry.builder()
					.id(UUID.randomUUID())
					.statusCode(200)
					.requestTimestamp(logDateOutsideOfDateRange)
					.requestedUrl(BASE_URL + "/dashboard/organization/person")
					.timeTakenMs(400L)
					.build()
			);
		
		appSourceEntriesSuccess = List.of(
				HttpLogEntry.builder()
					.id(UUID.randomUUID())
					.statusCode(200)
					.requestTimestamp(logDate)
					.requestedUrl(APP_GATEWAY_BASE_URL + "/test-app-source/organization/1")
					.timeTakenMs(100L)
					.build(),
				HttpLogEntry.builder()
					.id(UUID.randomUUID())
					.statusCode(200)
					.requestTimestamp(logDate)
					.requestedUrl(APP_GATEWAY_BASE_URL + "/test-app-source/person/1")
					.timeTakenMs(200L)
					.build(),
				HttpLogEntry.builder()
					.id(UUID.randomUUID())
					.statusCode(200)
					.requestTimestamp(logDate)
					.requestedUrl(APP_GATEWAY_BASE_URL + "/test-app-source/organization/person/1")
					.timeTakenMs(300L)
					.build()
			);
		
		appSourceEntriesFail = List.of(
				HttpLogEntry.builder()
					.id(UUID.randomUUID())
					.statusCode(403)
					.requestTimestamp(logDate)
					.requestedUrl(APP_GATEWAY_BASE_URL + "/test-app-source/organization/1")
					.timeTakenMs(100L)
					.build(),
				HttpLogEntry.builder()
					.id(UUID.randomUUID())
					.statusCode(500)
					.requestTimestamp(logDate)
					.requestedUrl(APP_GATEWAY_BASE_URL + "/test-app-source/person/1")
					.timeTakenMs(200L)
					.build(),
				HttpLogEntry.builder()
					.id(UUID.randomUUID())
					.statusCode(503)
					.requestTimestamp(logDate)
					.requestedUrl(APP_GATEWAY_BASE_URL + "/test-app-source/organization/person/1")
					.timeTakenMs(300L)
					.build()
			);
	
		personEntries = List.of(
				HttpLogEntry.builder()
					.id(UUID.randomUUID())
					.statusCode(200)
					.requestTimestamp(logDate)
					.requestedUrl(BASE_URL + "/person")
					.timeTakenMs(1000L)
					.build(),
				HttpLogEntry.builder()
					.id(UUID.randomUUID())
					.statusCode(200)
					.requestTimestamp(logDate)
					.requestedUrl(BASE_URL + "/person/organization/2")
					.timeTakenMs(1000L)
					.build(),
				HttpLogEntry.builder()
					.id(UUID.randomUUID())
					.statusCode(200)
					.requestTimestamp(logDate)
					.requestedUrl(BASE_URL + "/person/app/organization/2")
					.timeTakenMs(1000L)
					.build(),
				HttpLogEntry.builder()
					.id(UUID.randomUUID())
					.statusCode(200)
					.requestTimestamp(logDate)
					.requestedUrl(BASE_URL + "/person/app/organization/test-app-source/2")
					.timeTakenMs(2000L)
					.build()
			);

		orgEntries = List.of(
				HttpLogEntry.builder()
					.id(UUID.randomUUID())
					.statusCode(200)
					.requestTimestamp(logDate)
					.requestedUrl(BASE_URL + "/organization/person/1")
					.timeTakenMs(500L)
					.userName("app-client-user")
					.build(),
				HttpLogEntry.builder()
					.id(UUID.randomUUID())
					.statusCode(200)
					.requestTimestamp(logDate)
					.requestedUrl(BASE_URL + "/organization")
					.timeTakenMs(500L)
					.userName("dashboard@user.com")
					.build()
			);
	
		otherEntries = List.of(
				HttpLogEntry.builder()
					.id(UUID.randomUUID())
					.statusCode(200)
					.requestTimestamp(logDate)
					.requestedUrl(BASE_URL + "/dashboard/organization")
					.timeTakenMs(400L)
					.build()
			);
		
		allEntries = new ArrayList<>();
		Stream.of(entriesOutsideOfDateRange, appSourceEntriesSuccess, appSourceEntriesFail, personEntries, orgEntries, otherEntries).forEach(allEntries::addAll);
	
		repo.saveAll(allEntries);
	}
	
	@Test
	void getMetricsForSuccessfulResponsesByService_shouldReturn_whenUrlsAreLike() {
		/**
		 * Test to make sure that the pattern matching does not match resources
		 * to the wrong category/app source name.
		 * 
		 * For example: ensure http://localhost/api/v2/app/puckboard/organization/1
		 * is not captured along with http://localhost/api/v2/organization/1 in
		 * the database query
		 */
		var serviceMetrics = repo.getMetricsForSuccessfulResponsesByService(startDate, endDate, APP_GATEWAY_PREFIX);
		Optional<ServiceMetricDto> testAppSourceMetric = serviceMetrics.stream().filter(metric -> metric.getName().equalsIgnoreCase("test-app-source")).findAny();
		assertThat(testAppSourceMetric.get()).isNotNull();
		assertThat(testAppSourceMetric.get().getResponseCount()).isEqualTo(appSourceEntriesSuccess.size());
		assertThat(testAppSourceMetric.get().getAverageLatency()).isEqualTo(appSourceEntriesSuccess.stream().collect(Collectors.averagingLong(i -> i.getTimeTakenMs())));
		
		Optional<ServiceMetricDto> organizationMetric = serviceMetrics.stream().filter(metric -> metric.getName().equalsIgnoreCase("Organization")).findAny();
		assertThat(organizationMetric).isNotNull();
		assertThat(organizationMetric.get().getResponseCount()).isEqualTo(orgEntries.size());
		assertThat(organizationMetric.get().getAverageLatency()).isEqualTo(orgEntries.stream().collect(Collectors.averagingLong(i -> i.getTimeTakenMs())));
		
		Optional<ServiceMetricDto> personMetric = serviceMetrics.stream().filter(metric -> metric.getName().equalsIgnoreCase("Person")).findAny();
		assertThat(personMetric).isNotNull();
		assertThat(personMetric.get().getResponseCount()).isEqualTo(personEntries.size());
		assertThat(personMetric.get().getAverageLatency()).isEqualTo(personEntries.stream().collect(Collectors.averagingLong(i -> i.getTimeTakenMs())));
		
		Optional<ServiceMetricDto> otherMetric = serviceMetrics.stream().filter(metric -> metric.getName().equalsIgnoreCase("Other")).findAny();
		assertThat(otherMetric).isNotNull();
		assertThat(otherMetric.get().getResponseCount()).isEqualTo(otherEntries.size());
		assertThat(otherMetric.get().getAverageLatency()).isEqualTo(otherEntries.stream().collect(Collectors.averagingLong(i -> i.getTimeTakenMs())));
	}
	
	@Test
	void getUsersAccessingOrgRecords_shouldReturn_containingOnlyAccessorsOfOrganizationEndpoint() {
		var entries = repo.getUsersAccessingOrgRecords(startDate, endDate);
		assertThat(entries).hasSize(2);
		entries.forEach(entry -> {
			assertThat(orgEntries).anyMatch(orgEntry -> orgEntry.getUserName().equalsIgnoreCase(entry.getName()));
		});
	}
	
	@Test
	void getAppSourceUsage_shouldReturn_containingOnlySuccessfulEntriesToAppGatewayEndpoint() {
		var appSourceUsage = repo.getAppSourceUsage(startDate, endDate);
		assertThat(appSourceUsage)
			.hasSize(appSourceEntriesSuccess.size())
			.allMatch(entry -> {
				int status = entry.getStatusCode();
				
				return entry.getRequestedUrl().startsWith(APP_GATEWAY_BASE_URL) && status >= 200 && status < 300;
			});
	}
	
	@Test
	void getAppSourceErrorUsage_shouldReturn_containingOnlyErrorEntriesToAppGatewayEndpoint() {
		var appSourceErrorUsage = repo.getAppSourceErrorUsage(startDate, endDate);
		assertThat(appSourceErrorUsage)
			.hasSize(appSourceEntriesFail.size())
			.allMatch(entry -> {
				int status = entry.getStatusCode();
				
				return entry.getRequestedUrl().startsWith(APP_GATEWAY_BASE_URL) && status >= 400 && status < 600;
			});
	}
	
	@Test
	void getAverageLatencyForSuccessfulResponse_shouldReturn_onlyAverageOfSuccessResponses() {
		var avgLatency = repo.getAverageLatencyForSuccessfulResponse(startDate, endDate);
		
		double calculatedAvgLatency = allEntries
				.stream()
				.filter(entry -> {
					int status = entry.getStatusCode();
					Date timestamp = entry.getRequestTimestamp();
					
					return status >= 200 && status < 300 && timestamp.compareTo(startDate) >= 0 && timestamp.compareTo(endDate) <= 0;
				})
				.collect(Collectors.averagingLong(entry -> entry.getTimeTakenMs()));
		
		assertThat(avgLatency).contains(calculatedAvgLatency);
	}
}
