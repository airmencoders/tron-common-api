package mil.tron.commonapi.service.kpi;

import java.util.Date;
import java.util.List;

import mil.tron.commonapi.dto.kpi.KpiSummaryDto;
import mil.tron.commonapi.dto.kpi.UserWithRequestCount;

public interface KpiService {
	List<UserWithRequestCount> getUsersWithRequestCount(Date startDate, Date endDate);
	long getAppSourceCount();
	long getAverageLatencyForSuccessResponse(Date startDate, Date endDate);
	KpiSummaryDto aggregateKpis(Date startDate, Date endDate);
}
