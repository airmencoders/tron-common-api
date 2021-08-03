package mil.tron.commonapi.repository.kpi;

import java.util.Date;
import java.util.List;
import java.util.UUID;


import org.springframework.data.repository.CrudRepository;

import mil.tron.commonapi.entity.kpi.KpiSummary;

public interface KpiRepository extends CrudRepository<KpiSummary, UUID> {
	List<KpiSummary> findByStartDateBetween(Date start, Date end);
}
