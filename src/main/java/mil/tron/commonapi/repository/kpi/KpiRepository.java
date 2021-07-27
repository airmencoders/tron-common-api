package mil.tron.commonapi.repository.kpi;

import java.util.UUID;


import org.springframework.data.repository.CrudRepository;

import mil.tron.commonapi.entity.kpi.KpiSummary;

public interface KpiRepository extends CrudRepository<KpiSummary, UUID> {
	
}
