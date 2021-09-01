package mil.tron.commonapi.service.kpi;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import mil.tron.commonapi.dto.kpi.KpiSummaryDto;
import mil.tron.commonapi.dto.kpi.ServiceMetricDto;
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
import mil.tron.commonapi.service.utility.HttpLogsUtilService;

@Service
public class KpiServiceImpl implements KpiService {
	private final DtoMapper modelMapper;
	private final HttpLogsRepository httpLogsRepo;
	private final AppSourceRepository appSourceRepo;
	private final MeterValueRepository meterValueRepo;
	private final KpiRepository kpiRepo;
	private final HttpLogsUtilService httpLogsUtilService;
	private final Clock systemUtcClock;
	private final String appGatewayPrefix;
	
	public KpiServiceImpl(
			HttpLogsRepository httpLogsRepo, 
			AppSourceRepository appSourceRepo, 
			MeterValueRepository meterValueRepo, 
			KpiRepository kpiRepo,
			HttpLogsUtilService httpLogsService,
			Clock systemUtcClock,
			@Value("${app-sources-prefix}") String appGatewayPrefix) {
		this.httpLogsRepo = httpLogsRepo;
		this.appSourceRepo = appSourceRepo;
		this.meterValueRepo = meterValueRepo;
		this.httpLogsUtilService = httpLogsService;
		this.kpiRepo = kpiRepo;
		
		this.systemUtcClock = systemUtcClock;
		
		this.appGatewayPrefix = appGatewayPrefix;
		
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
	public Double getAverageLatencyForSuccessResponse(Date startDate, Date endDate) {
		return this.httpLogsRepo.getAverageLatencyForSuccessfulResponse(startDate, endDate).orElse(null);
	}

	@Override
	public KpiSummaryDto aggregateKpis(Date startDate, @Nullable Date endDate) {
		Date now = Date.from(Instant.now(systemUtcClock));
		
		if (endDate == null) {
    		endDate = now;
    	}
    	
		httpLogsUtilService.isDateInThePast(startDate, now, true);
		httpLogsUtilService.isDateBeforeOrEqualTo(startDate, endDate, true);
    	
		// Ensure endDate gets 23:59:59 to be inclusive of the day
		endDate = httpLogsUtilService.getDateAtEndOfDay(endDate);
		
		// Ensure startDate gets 00:00:00
		startDate = httpLogsUtilService.getDateAtStartOfDay(startDate);
		
		List<UserWithRequestCount> userRequestCounts = this.getUsersWithRequestCount(startDate, endDate);
		List<UserWithRequestCount> dashboardUsers = new ArrayList<>();
		List<UserWithRequestCount> appClients = new ArrayList<>();
		// Split the users into Dashboard users and App Client users based on the name.
		// If the name includes a domain, consider it a Dashboard user (eg: contains @something.test)
		// and everything else will be an App Client.
		userRequestCounts.forEach(userRequestCount -> {
			if (!httpLogsUtilService.isUsernameAnAppClient(userRequestCount.getName())) {
				dashboardUsers.add(userRequestCount);
			} else {
				appClients.add(userRequestCount);
			}
		});
		
		List<AppSourceMetricSummary> appSourceMetricsSummary = this.meterValueRepo.getAllAppSourceMetricsSummary(startDate, endDate);
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
		
		List<ServiceMetricDto> serviceMetrics = this.getServiceMetrics(startDate, endDate);
		
		return KpiSummaryDto.builder()
				.startDate(startDate)
				.endDate(endDate)
				.appSourceCount(this.getAppSourceCount())
				.averageLatencyForSuccessfulRequests(this.getAverageLatencyForSuccessResponse(startDate, endDate))
				.appClientToAppSourceRequestCount(appClientToAppSourceRequestCount)
				.uniqueVisitorCounts(uniqueVisitorCount)
				.serviceMetrics(serviceMetrics)
				.build();
	}
	
	@Override
	public KpiSummaryDto saveAggregatedKpis(KpiSummaryDto dto) {
		KpiSummary entity = convertToEntity(dto);
		KpiSummary savedEntity = kpiRepo.save(entity);
		
		return convertToDto(savedEntity);
	}

	@Override
	public List<KpiSummaryDto> getKpisRangeOnStartDateBetween(Date startDate, @Nullable Date endDate) {
		LocalDate thisWeekMonday = LocalDate.now(systemUtcClock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		Date thisWeekMondayAsDate = Date.from(thisWeekMonday.atStartOfDay(ZoneId.of("UTC")).toInstant());
		
		/**
		 * Get last week Monday based off the passed in Start Date and
		 * use that as the new Start Date.
		 * 
		 * For example, given a start date of 2021-07-28 (Wednesday), getLastWeekMondayFromStartDate should
		 * equal to 2021-07-19 (Monday).
		 * 
		 * Given 2021-07-26 (Monday), getLastWeekMondayFromStartDate should be 2021-07-19 (Monday)
		 */
		LocalDate getLastWeekFromStartDate = LocalDate.ofInstant(startDate.toInstant(), ZoneId.of("UTC")).minusWeeks(1);
		LocalDate getLastWeekMondayFromStartDate = getLastWeekFromStartDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		
		startDate = Date.from(getLastWeekMondayFromStartDate.atStartOfDay(ZoneId.of("UTC")).toInstant());
		
		/**
		 * If Start Date ends up being within this week, throw exception
		 * since there is a guarantee that there would be no data.
		 */
		if (startDate.compareTo(thisWeekMondayAsDate) >= 0) {
			throw new BadRequestException("Start Date cannot be set within the current week or the future");
		}
		
		if (endDate == null) {
    		endDate = Date.from(Instant.now(systemUtcClock));
    	}
    	
		httpLogsUtilService.isDateBeforeOrEqualTo(startDate, endDate, true);
    	
		return kpiRepo.findByStartDateBetween(startDate, endDate)
				.stream()
				.map(this::convertToDto)
				.collect(Collectors.toList());
	}
	
	private KpiSummaryDto convertToDto(KpiSummary entity) {
		return modelMapper.map(entity, KpiSummaryDto.class);
	}
	
	private KpiSummary convertToEntity(KpiSummaryDto dto) {
		return modelMapper.map(dto, KpiSummary.class);
	}
	
	/**
	 * Scheduled task to run every Monday at 00:00:00 (12:00:00 am) that gets aggregated
	 * KPI data for the previous week and save it to the database.
	 */
	@Scheduled(cron = "0 0 0 * * MON", zone = "UTC")
	private void weeklyAggregatedKpisTask() {
		LocalDate today = LocalDate.now(systemUtcClock);
		LocalDate startOfLastWeek = today.with(TemporalAdjusters.previous(DayOfWeek.MONDAY));
		LocalDate endOfLastWeek = startOfLastWeek.with(TemporalAdjusters.next(DayOfWeek.SUNDAY));
		
		Date startDate = Date.from(startOfLastWeek.atStartOfDay(ZoneId.of("UTC")).toInstant());
		Date endDate = Date.from(endOfLastWeek.atStartOfDay(ZoneId.of("UTC")).toInstant());
		KpiSummaryDto aggregatedKpis = aggregateKpis(startDate, endDate);
		
		saveAggregatedKpis(aggregatedKpis);
	}

	@Override
	public List<ServiceMetricDto> getServiceMetrics(Date startDate, @Nullable Date endDate) {
		Date now = Date.from(Instant.now(systemUtcClock));
		
		if (endDate == null) {
    		endDate = now;
    	}
    	
		httpLogsUtilService.isDateInThePast(startDate, now, true);
		httpLogsUtilService.isDateBeforeOrEqualTo(startDate, endDate, true);
    	
		// Ensure endDate gets 23:59:59 to be inclusive of the day
		endDate = httpLogsUtilService.getDateAtEndOfDay(endDate);
		
		// Ensure startDate gets 00:00:00
		startDate = httpLogsUtilService.getDateAtStartOfDay(startDate);
		
		return httpLogsRepo.getMetricsForSuccessfulResponsesByService(startDate, endDate, appGatewayPrefix);
	}
}
