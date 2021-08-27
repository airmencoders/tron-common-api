package mil.tron.commonapi.dto.kpi;

import lombok.Getter;
import lombok.Setter;
import mil.tron.commonapi.entity.kpi.ServiceMetric;

public class ServiceMetricDto implements ServiceMetric {
	@Getter
	@Setter
	private String name;
	
	@Getter
	@Setter
	private Long averageLatency;
	
	@Getter
	@Setter
	private Long responseCount;
}
