package mil.tron.commonapi.service.kpi;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import mil.tron.commonapi.dto.kpi.KpiSummaryDto;
import mil.tron.commonapi.dto.kpi.UniqueVisitorCountDto;
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

@Service
public class KpiServiceImpl implements KpiService {
	private static final Pattern EMAIL_DOMAIN_PATTERN = Pattern.compile("[@].+[.].+");

	private final DtoMapper modelMapper;
	
	private HttpLogsRepository httpLogsRepo;
	private AppSourceRepository appSourceRepo;
	private MeterValueRepository meterValueRepo;
	private KpiRepository kpiRepo;
	private Clock systemUtcClock;
	
	public KpiServiceImpl(HttpLogsRepository httpLogsRepo, AppSourceRepository appSourceRepo, MeterValueRepository meterValueRepo, KpiRepository kpiRepo, Clock systemUtcClock) {
		this.httpLogsRepo = httpLogsRepo;
		this.appSourceRepo = appSourceRepo;
		this.meterValueRepo = meterValueRepo;
		this.kpiRepo = kpiRepo;
		
		this.systemUtcClock = systemUtcClock;
		
		this.modelMapper = new DtoMapper();
	}

	@Override
	public List<UserWithRequestCount> getUsersWithRequestCount(Date startDate, Date endDate) {
		return this.httpLogsRepo.getUsersWithRequestCount(startDate, endDate);
	}

	@Override
	public Long getAppSourceCount() {
		return this.appSourceRepo.countByAvailableAsAppSourceTrue().orElse(0L);
	}

	@Override
	public Long getAverageLatencyForSuccessResponse(Date startDate, Date endDate) {
		return this.httpLogsRepo.getAverageLatencyForSuccessfulResponse(startDate, endDate).orElse(null);
	}

	@Override
	public KpiSummaryDto aggregateKpis(LocalDate startDate, LocalDate endDate) {
		if (startDate.isAfter(LocalDate.now(systemUtcClock))) {
			throw new BadRequestException("Start Date cannot be in the future");
		}
		
    	if (endDate == null) {
    		endDate = LocalDate.now(systemUtcClock);
    	}
    	
    	if (startDate.compareTo(endDate) > 0) {
    		
            throw new BadRequestException("Start date must be before or equal to End Date");
        }
    	
		var start = Date.from(startDate.atStartOfDay(ZoneId.of("UTC")).toInstant());
		var end = Date.from(endDate.atStartOfDay(ZoneId.of("UTC")).toInstant());
		
    	Calendar now = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		now.setTime(end);
		now.set(Calendar.HOUR_OF_DAY, 23);
		now.set(Calendar.MINUTE, 59);
		now.set(Calendar.SECOND, 59);
		end = now.getTime();
		
		List<UserWithRequestCount> userRequestCounts = this.getUsersWithRequestCount(start, end);
		List<UserWithRequestCount> dashboardUsers = new ArrayList<>();
		List<UserWithRequestCount> appClients = new ArrayList<>();
		// Split the users into Dashboard users and App Client users based on the name.
		// If the name includes a domain, consider it a Dashboard user (eg: contains @something.test)
		// and everything else will be an App Client.
		userRequestCounts.forEach(userRequestCount -> {
			Matcher emailMatcher = EMAIL_DOMAIN_PATTERN.matcher(userRequestCount.getName());
			if (emailMatcher.find()) {
				dashboardUsers.add(userRequestCount);
			} else {
				appClients.add(userRequestCount);
			}
		});
		
		List<AppSourceMetricSummary> appSourceMetricsSummary = this.meterValueRepo.getAllAppSourceMetricsSummary(start, end);
		long appClientToAppSourceRequestCount = appSourceMetricsSummary.stream().collect(Collectors.summingLong(AppSourceMetricSummary::getRequestCount));
		
		long dashboardUserCount = dashboardUsers.size();
		long dashboardUserRequestCount = dashboardUsers.stream().collect(Collectors.summingLong(UserWithRequestCount::getRequestCount));
		long appClientUserCount = appClients.size();
		long appClientUserRequestCount = appClients.stream().collect(Collectors.summingLong(UserWithRequestCount::getRequestCount));
		List<UniqueVisitorCountDto> uniqueVisitorCount = new ArrayList<>();
		uniqueVisitorCount.add(UniqueVisitorCountDto.builder()
				.visitorType(VisitorType.DASHBOARD_USER)
				.uniqueCount(dashboardUserCount)
				.requestCount(dashboardUserRequestCount)
				.build());
		
		uniqueVisitorCount.add(UniqueVisitorCountDto.builder()
				.visitorType(VisitorType.APP_CLIENT)
				.uniqueCount(appClientUserCount)
				.requestCount(appClientUserRequestCount)
				.build());
		
		return KpiSummaryDto.builder()
				.startDate(startDate)
				.endDate(endDate)
				.appSourceCount(this.getAppSourceCount())
				.averageLatencyForSuccessfulRequests(this.getAverageLatencyForSuccessResponse(start, end))
				.appClientToAppSourceRequestCount(appClientToAppSourceRequestCount)
				.uniqueVisitorCounts(uniqueVisitorCount)
				.build();
	}
	
	@Override
	public KpiSummaryDto saveAggregatedKpis(KpiSummaryDto dto) {
		KpiSummary entity = modelMapper.map(dto, KpiSummary.class);
		KpiSummary savedEntity = kpiRepo.save(entity);
		
		return modelMapper.map(savedEntity, KpiSummaryDto.class);
	}
	
	@Scheduled(cron = "0 0 0 * * MON", zone = "UTC")
	private void weeklyAggregatedKpisTask() {
		LocalDate today = LocalDate.now(systemUtcClock);
		LocalDate endOfWeek = today.minusDays(1);
		LocalDate startOfWeek = today.minusDays(7);
		
		KpiSummaryDto aggregatedKpis = aggregateKpis(startOfWeek, endOfWeek);
		
		saveAggregatedKpis(aggregatedKpis);
	}
}
