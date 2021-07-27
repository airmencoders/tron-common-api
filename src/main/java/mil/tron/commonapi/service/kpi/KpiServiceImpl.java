package mil.tron.commonapi.service.kpi;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import mil.tron.commonapi.dto.kpi.KpiSummaryDto;
import mil.tron.commonapi.dto.kpi.UniqueVisitorSummaryDto;
import mil.tron.commonapi.entity.kpi.AppSourceMetricSummary;
import mil.tron.commonapi.entity.kpi.UserWithRequestCount;
import mil.tron.commonapi.repository.HttpLogsRepository;
import mil.tron.commonapi.repository.MeterValueRepository;
import mil.tron.commonapi.repository.appsource.AppSourceRepository;

@Service
public class KpiServiceImpl implements KpiService {
	private static final Pattern EMAIL_DOMAIN_PATTERN = Pattern.compile("[@].+[.].+");
	
	
	private HttpLogsRepository httpLogsRepo;
	private AppSourceRepository appSourceRepo;
	private MeterValueRepository meterValueRepo;
	
	public KpiServiceImpl(HttpLogsRepository httpLogsRepo, AppSourceRepository appSourceRepo, MeterValueRepository meterValueRepo) {
		this.httpLogsRepo = httpLogsRepo;
		this.appSourceRepo = appSourceRepo;
		this.meterValueRepo = meterValueRepo;
	}

	@Override
	public List<UserWithRequestCount> getUsersWithRequestCount(Date startDate, Date endDate) {
		return this.httpLogsRepo.getUsersWithRequestCount(startDate, endDate);
	}

	@Override
	public Long getAppSourceCount() {
		return this.appSourceRepo.countByAvailableAsAppSourceTrue().orElse(null);
	}

	@Override
	public Long getAverageLatencyForSuccessResponse(Date startDate, Date endDate) {
		return this.httpLogsRepo.getAverageLatencyForSuccessfulResponse(startDate, endDate).orElse(null);
	}

	@Override
	public KpiSummaryDto aggregateKpis(Date startDate, Date endDate) {
		List<UserWithRequestCount> userRequestCounts = this.getUsersWithRequestCount(startDate, endDate);
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
		
		List<AppSourceMetricSummary> appSourceMetricsSummary = this.meterValueRepo.getAllAppSourceMetricsSummary(startDate, endDate);
		long appClientToAppSourceRequestCount = appSourceMetricsSummary.stream().collect(Collectors.summingLong(AppSourceMetricSummary::getRequestCount));
		
		long dashboardUserCount = dashboardUsers.size();
		long dashboardUserRequestCount = dashboardUsers.stream().collect(Collectors.summingLong(UserWithRequestCount::getRequestCount));
		long appClientUserCount = appClients.size();
		long appClientUserRequestCount = appClients.stream().collect(Collectors.summingLong(UserWithRequestCount::getRequestCount));
		
		return KpiSummaryDto.builder()
				.appSourceCount(this.getAppSourceCount())
				.averageLatencyForSuccessfulRequests(this.getAverageLatencyForSuccessResponse(startDate, endDate))
				.appClientToAppSourceRequestCount(appClientToAppSourceRequestCount)
				.uniqueVisitorySummary(UniqueVisitorSummaryDto.builder()
						.appClientCount(appClientUserCount)
						.appClientRequestCount(appClientUserRequestCount)
						.dashboardUserCount(dashboardUserCount)
						.dashboardUserRequestCount(dashboardUserRequestCount)
						.build()
					)
				.build();
	}
}
