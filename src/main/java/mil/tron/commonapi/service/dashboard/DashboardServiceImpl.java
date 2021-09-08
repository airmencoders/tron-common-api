package mil.tron.commonapi.service.dashboard;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.modelmapper.ModelMapper;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import mil.tron.commonapi.dto.dashboard.AppSourceErrorResponseDto;
import mil.tron.commonapi.dto.dashboard.AppSourceErrorUsageDto;
import mil.tron.commonapi.dto.dashboard.ResponseDto;
import mil.tron.commonapi.dto.dashboard.AppSourceUsageDto;
import mil.tron.commonapi.dto.dashboard.AppSourceUsageResponseDto;
import mil.tron.commonapi.dto.dashboard.EntityAccessorDto;
import mil.tron.commonapi.dto.dashboard.EntityAccessorResponseDto;
import mil.tron.commonapi.entity.HttpLogEntry;
import mil.tron.commonapi.entity.dashboard.EntityAccessor;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.repository.HttpLogsRepository;
import mil.tron.commonapi.service.utility.HttpLogsUtilService;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;

@Slf4j
@Service
public class DashboardServiceImpl implements DashboardService {
	private final ModelMapper modelMapper;
	
	private HttpLogsUtilService httpLogsUtilService;
	private HttpLogsRepository httpLogsRepo;
	
	private Clock systemUtcClock;

	public DashboardServiceImpl(HttpLogsUtilService httpLogsService, HttpLogsRepository httpLogsRepo, Clock systemUtcClock) {
		modelMapper = new ModelMapper();
		
		this.httpLogsUtilService = httpLogsService;
		this.httpLogsRepo = httpLogsRepo;
		
		this.systemUtcClock = systemUtcClock;
	}

	@Override
	public EntityAccessorResponseDto getAppClientsAccessingOrgRecords(@NonNull Date startDate, @Nullable Date endDate) {
		Date now = Date.from(Instant.now(systemUtcClock));
		if (endDate == null) {
    		endDate = now;
    	}
		
		validateDates(startDate, endDate, now);
    	
		List<EntityAccessor> organizationAccessors = this.httpLogsRepo.getUsersAccessingOrgRecords(startDate, endDate);

		List<EntityAccessorDto> appClientAccessors = organizationAccessors.stream()
				.filter(accessor -> httpLogsUtilService.isUsernameAnAppClient(accessor.getName()))
				.map(accessor -> modelMapper.map(accessor, EntityAccessorDto.class))
				.collect(Collectors.toList());
		
		return EntityAccessorResponseDto.builder()
				.entityAccessors(appClientAccessors)
				.startDate(startDate)
				.endDate(endDate)
				.build();
	}

	@Override
	public EntityAccessorResponseDto getAppClientsAccessingPersonnelRecords(@NonNull Date startDate, @Nullable Date endDate) {
		Date now = Date.from(Instant.now(systemUtcClock));
		if (endDate == null) {
    		endDate = now;
    	}
		
		validateDates(startDate, endDate, now);
    	
		List<EntityAccessor> organizationAccessors = this.httpLogsRepo.getUsersAccessingPersonnelRecords(startDate, endDate);

		List<EntityAccessorDto> appClientAccessors = organizationAccessors.stream()
				.filter(accessor -> httpLogsUtilService.isUsernameAnAppClient(accessor.getName()))
				.map(accessor -> modelMapper.map(accessor, EntityAccessorDto.class))
				.collect(Collectors.toList());
		
		return EntityAccessorResponseDto.builder()
				.entityAccessors(appClientAccessors)
				.startDate(startDate)
				.endDate(endDate)
				.build();
	}

	@Override
	public AppSourceUsageResponseDto getAppSourceUsage(@NonNull Date startDate, @Nullable Date endDate, boolean descending, long limit) {
		Date now = Date.from(Instant.now(systemUtcClock));
		if (endDate == null) {
    		endDate = now;
    	}
		
		validateDates(startDate, endDate, now);
		
		List<HttpLogEntry> appSourceUsage = this.httpLogsRepo.getAppSourceUsage(startDate, endDate);

		Map<String, Long> appSourceUsageCount = appSourceUsage.stream()
				.collect(Collectors.groupingBy(entry -> getAppSourceNameFromRequestUrl(entry.getRequestedUrl()), Collectors.counting()));
		
		// Remove empty string from the map as this references request urls that
		// App Source names could not be extracted from.
		appSourceUsageCount.remove("");
		
		LinkedList<AppSourceUsageDto> usage = appSourceUsageCount.entrySet().stream()
				.sorted(descending ? Map.Entry.comparingByValue(Comparator.reverseOrder()) : Map.Entry.comparingByValue())
				.limit(limit)
				.map(entry -> AppSourceUsageDto.builder().name(entry.getKey()).incomingRequestCount(entry.getValue()).build())
				.collect(Collectors.toCollection(LinkedList::new));
		
		return AppSourceUsageResponseDto.builder()
				.appSourceUsage(usage)
				.startDate(startDate)
				.endDate(endDate)
				.build();
	}

	@Override
	public AppSourceErrorResponseDto getAppSourceErrorUsage(@NonNull Date startDate, @Nullable Date endDate) {
		Date now = Date.from(Instant.now(systemUtcClock));
		if (endDate == null) {
    		endDate = now;
    	}
		
		validateDates(startDate, endDate, now);
		
		List<HttpLogEntry> appSourceErrorUsage = this.httpLogsRepo.getAppSourceErrorUsage(startDate, endDate);
		
		Map<String, Map<Integer, Long>> appSourceErrorUsageCount = appSourceErrorUsage.stream()
				.collect(Collectors.groupingBy(entry -> getAppSourceNameFromRequestUrl(entry.getRequestedUrl()),
						Collectors.groupingBy(HttpLogEntry::getStatusCode, Collectors.counting())));
		
		// Remove empty string from the map as this references request urls that
		// App Source names could not be extracted from.
		appSourceErrorUsageCount.remove("");
		
		List<AppSourceErrorUsageDto> usage = appSourceErrorUsageCount.entrySet().stream()
				.map(entry -> {
					List<ResponseDto> errorResponses = entry.getValue().entrySet().stream()
							.map(errorEntry -> ResponseDto.builder()
									.statusCode(errorEntry.getKey())
									.count(errorEntry.getValue())
									.build())
							.collect(Collectors.toList());
					
					Long totalErrorResponses = errorResponses.stream().mapToLong(ResponseDto::getCount).sum();
					
					return AppSourceErrorUsageDto.builder()
						.name(entry.getKey())
						.errorResponses(errorResponses)
						.totalErrorResponses(totalErrorResponses)
						.build();
				})
				.collect(Collectors.toList());
		
		return AppSourceErrorResponseDto.builder()
				.appSourceUsage(usage)
				.startDate(startDate)
				.endDate(endDate)
				.build();
	}
	
	/**
	 * Validates that:
	 * {@code startDate} is not in the future (compared against {@code referenceDate}).
	 * {@code startDate} is before or equal to {@code endDate}
	 * @param startDate the start date to compare
	 * @param endDate the end date to compare
	 * @param referenceDate the reference date to compare as now
	 * 
	 * @throws BadRequestException if any of the rules are violated.
	 */
	private void validateDates(Date startDate, Date endDate, Date referenceDate) {
		if (startDate.after(referenceDate)) {
			throw new BadRequestException("Start Date cannot be in the future");
		}
		
    	if (startDate.compareTo(endDate) > 0) {
            throw new BadRequestException("Start Date must be before or equal to End Date");
        }
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
