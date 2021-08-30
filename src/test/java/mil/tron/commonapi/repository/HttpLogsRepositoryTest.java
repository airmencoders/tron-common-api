package mil.tron.commonapi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import mil.tron.commonapi.entity.HttpLogEntry;

@SpringBootTest
class HttpLogsRepositoryTest {
	private static final String BASE_URL = "http://localhost/api/v2/";
	
	@Autowired
	private HttpLogsRepository repo;
	
	@BeforeEach
	void setup() {
		repo.deleteAll();
	}
	
	@Test
	void getMetricsForSuccessfulResponsesByService_shouldReturn_whenUrlsAreLike() {
		/**
		 * Make sure app gateway urls do not end up getting matched to internal
		 * resource urls.
		 * 
		 * For example: ensure http://localhost/api/v2/app/puckboard/organization/1
		 * is not captured along with http://localhost/api/v2/organization/1 in
		 * the database query
		 */
		
		// Sunday, August 29, 2021 1:00:00
		Date logDate = new Date(1630198800000L);
		
		repo.save(HttpLogEntry.builder()
				.id(UUID.randomUUID())
				.statusCode(200)
				.requestTimestamp(logDate)
				.requestedUrl(BASE_URL + "app/puckboard/organization/1")
				.build());
		
		repo.save(HttpLogEntry.builder()
				.id(UUID.randomUUID())
				.statusCode(200)
				.requestTimestamp(logDate)
				.requestedUrl(BASE_URL + "app/puckboard/person/1")
				.build());
		
		repo.save(HttpLogEntry.builder()
				.id(UUID.randomUUID())
				.statusCode(200)
				.requestTimestamp(logDate)
				.requestedUrl(BASE_URL + "app/puckboard/organization/person/1")
				.build());
		
		repo.save(HttpLogEntry.builder()
				.id(UUID.randomUUID())
				.statusCode(200)
				.requestTimestamp(logDate)
				.requestedUrl(BASE_URL + "app/organization-test/person/1")
				.build());
		
		repo.save(HttpLogEntry.builder()
				.id(UUID.randomUUID())
				.statusCode(200)
				.requestTimestamp(logDate)
				.requestedUrl(BASE_URL + "person/1")
				.build());
		
		repo.save(HttpLogEntry.builder()
				.id(UUID.randomUUID())
				.statusCode(200)
				.requestTimestamp(logDate)
				.requestedUrl(BASE_URL + "organization/1")
				.build());
		
		// Saturday, August 28, 2021 1:00:00
		Date startDate = new Date(1630112400000L);
		// Monday, August 30, 2021 1:00:00
		Date endDate = new Date(1630285200000L);
		
		var serviceMetrics = repo.getMetricsForSuccessfulResponsesByService(startDate, endDate, "/app");
		var puckboardMetric = serviceMetrics.stream().filter(metric -> metric.getName().equalsIgnoreCase("puckboard")).findAny();
		assertThat(puckboardMetric).isNotNull();
		assertThat(puckboardMetric.get().getResponseCount()).isEqualTo(3);
		
		var organizationTestMetric = serviceMetrics.stream().filter(metric -> metric.getName().equalsIgnoreCase("organization-test")).findAny();
		assertThat(organizationTestMetric).isNotNull();
		assertThat(organizationTestMetric.get().getResponseCount()).isEqualTo(1);
		
		var organizationMetric = serviceMetrics.stream().filter(metric -> metric.getName().equalsIgnoreCase("Person")).findAny();
		assertThat(organizationMetric).isNotNull();
		assertThat(organizationMetric.get().getResponseCount()).isEqualTo(1);
		
		var personMetric = serviceMetrics.stream().filter(metric -> metric.getName().equalsIgnoreCase("Organization")).findAny();
		assertThat(personMetric).isNotNull();
		assertThat(personMetric.get().getResponseCount()).isEqualTo(1);
	}
}
