package mil.tron.commonapi.service.dashboard;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import mil.tron.commonapi.dto.dashboard.AppSourceErrorRequestCountDto;
import mil.tron.commonapi.dto.dashboard.AppSourceErrorResponseDto;
import mil.tron.commonapi.dto.dashboard.AppSourceRequestCountDto;
import mil.tron.commonapi.entity.HttpLogEntry;
import mil.tron.commonapi.repository.HttpLogsRepository;
import mil.tron.commonapi.service.utility.HttpLogsUtilService;

import java.util.Comparator;

@Slf4j
@Service
public class DashboardServiceImpl implements DashboardService {
	private HttpLogsUtilService httpLogsService;
	private HttpLogsRepository httpLogsRepo;

	public DashboardServiceImpl(HttpLogsUtilService httpLogsService, HttpLogsRepository httpLogsRepo) {
		this.httpLogsService = httpLogsService;
		this.httpLogsRepo = httpLogsRepo;
	}

	@Override
	public List<String> getAppClientsAccessingOrgRecords(Date startDate, Date endDate) {
		List<String> organizationAccessors = this.httpLogsRepo.getUsersAccessingOrgRecords(startDate, endDate);

		return organizationAccessors.stream().filter(accessor -> {
			Matcher emailMatcher = httpLogsService.getEmailDomainPattern().matcher(accessor);
			return !emailMatcher.find();
		}).collect(Collectors.toList());
	}

	@Override
	public List<AppSourceRequestCountDto> getAppSourceUsage(Date startDate, Date endDate, boolean descending, long limit) {
		List<HttpLogEntry> appSourceUsage = this.httpLogsRepo.getAppSourceUsage(startDate, endDate);

		Map<String, Long> appSourceUsageCount = appSourceUsage.stream()
				.collect(Collectors.groupingBy(entry -> getAppSourceNameFromRequestUrl(entry.getRequestedUrl()), Collectors.counting()));
		
		// Remove empty string from the map as this references request urls that
		// App Source names could not be extracted from.
		appSourceUsageCount.remove("");
		
		return appSourceUsageCount.entrySet().stream()
				.sorted(descending ? Map.Entry.comparingByValue(Comparator.reverseOrder()) : Map.Entry.comparingByValue())
				.limit(limit)
				.map(entry -> AppSourceRequestCountDto.builder().name(entry.getKey()).requestCount(entry.getValue()).build())
				.collect(Collectors.toCollection(LinkedList::new));
	}

	@Override
	public List<AppSourceErrorRequestCountDto> getAppSourceErrorUsage(Date startDate, Date endDate) {
		List<HttpLogEntry> appSourceErrorUsage = this.httpLogsRepo.getAppSourceErrorUsage(startDate, endDate);
		
		Map<String, Map<Integer, Long>> appSourceErrorUsageCount = appSourceErrorUsage.stream()
				.collect(Collectors.groupingBy(entry -> getAppSourceNameFromRequestUrl(entry.getRequestedUrl()),
						Collectors.groupingBy(HttpLogEntry::getStatusCode, Collectors.counting())));
		
		// Remove empty string from the map as this references request urls that
		// App Source names could not be extracted from.
		appSourceErrorUsageCount.remove("");
				
		return appSourceErrorUsageCount.entrySet().stream()
				.map(entry -> AppSourceErrorRequestCountDto.builder()
						.name(entry.getKey())
						.errorResponses(entry.getValue().entrySet().stream()
								.map(errorEntry -> AppSourceErrorResponseDto.builder()
										.statusCode(errorEntry.getKey())
										.count(errorEntry.getValue())
										.build())
								.collect(Collectors.toList()))
						.build())
				.collect(Collectors.toList());
	}
	
	/**
	 * Parses a request url targeting an App Source to extract the App Source's name.
	 * Expected url format: http://localhost:8888/api/v1/app/APP_SOURCE_NAME/app_source_path/
	 * 
	 * @param url the request url to parse
	 * @return the App Source name or "" (empty string) if an error occurred
	 */
	private String getAppSourceNameFromRequestUrl(String url) {
		try {
			return url.split("/app/")[1].split("/")[0];
		} catch (Exception ex) {
			// If processing App Source name fails, just leave the result
			// as an empty string and remove it later.
			log.error("Could not parse App Source name from request url: " + url);
			return "";
		}
	}
}
