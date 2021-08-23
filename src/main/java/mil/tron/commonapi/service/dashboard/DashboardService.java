package mil.tron.commonapi.service.dashboard;

import java.util.Date;
import java.util.List;

import mil.tron.commonapi.dto.dashboard.AppSourceErrorRequestCountDto;
import mil.tron.commonapi.dto.dashboard.AppSourceRequestCountDto;

public interface DashboardService {
	List<String> getAppClientsAccessingOrgRecords(Date startDate, Date endDate);
	List<AppSourceRequestCountDto> getAppSourceUsage(Date startDate, Date endDate, boolean descending, long limit);
	List<AppSourceErrorRequestCountDto>  getAppSourceErrorUsage(Date startDate, Date endDate);
}
