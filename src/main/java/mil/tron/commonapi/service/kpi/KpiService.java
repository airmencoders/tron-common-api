package mil.tron.commonapi.service.kpi;

import java.util.Date;
import java.util.List;

import mil.tron.commonapi.dto.kpi.KpiSummaryDto;
import mil.tron.commonapi.dto.kpi.ServiceMetricDto;
import mil.tron.commonapi.entity.kpi.UserWithRequestCount;

public interface KpiService {
	List<UserWithRequestCount> getUsersWithRequestCount(Date startDate, Date endDate);
	Long getAppSourceCount();
	Double getAverageLatencyForSuccessResponse(Date startDate, Date endDate);
	KpiSummaryDto aggregateKpis(Date startDate, Date endDate);
	KpiSummaryDto saveAggregatedKpis(KpiSummaryDto dto);
	List<KpiSummaryDto> getKpisRangeOnStartDateBetween(Date startDate, Date endDate);
	List<ServiceMetricDto> getServiceMetrics(Date startDate, Date endDate);
}
