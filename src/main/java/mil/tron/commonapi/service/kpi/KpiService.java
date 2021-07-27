package mil.tron.commonapi.service.kpi;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import mil.tron.commonapi.dto.kpi.KpiSummaryDto;
import mil.tron.commonapi.entity.kpi.UserWithRequestCount;

public interface KpiService {
	List<UserWithRequestCount> getUsersWithRequestCount(Date startDate, Date endDate);
	Long getAppSourceCount();
	Long getAverageLatencyForSuccessResponse(Date startDate, Date endDate);
	KpiSummaryDto aggregateKpis(LocalDate startDate, LocalDate endDate);
	KpiSummaryDto saveAggregatedKpis(KpiSummaryDto dto);
}
