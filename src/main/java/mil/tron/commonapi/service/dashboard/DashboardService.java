package mil.tron.commonapi.service.dashboard;

import java.util.Date;

import mil.tron.commonapi.dto.dashboard.AppSourceErrorResponseDto;
import mil.tron.commonapi.dto.dashboard.AppSourceUsageResponseDto;
import mil.tron.commonapi.dto.dashboard.EntityAccessorResponseDto;

public interface DashboardService {
	EntityAccessorResponseDto getAppClientsAccessingOrgRecords(Date startDate, Date endDate);
	AppSourceUsageResponseDto getAppSourceUsage(Date startDate, Date endDate, boolean descending, long limit);
	AppSourceErrorResponseDto getAppSourceErrorUsage(Date startDate, Date endDate);
}
