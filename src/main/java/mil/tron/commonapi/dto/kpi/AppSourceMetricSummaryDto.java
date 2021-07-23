package mil.tron.commonapi.dto.kpi;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import mil.tron.commonapi.entity.kpi.AppSourceMetricSummary;

@Data
@Builder
public class AppSourceMetricSummaryDto implements AppSourceMetricSummary {
	@Getter
	@Setter
	private String appSourceName;
	
	@Getter
	@Setter
	private String appClientName;
	
	@Getter
	@Setter
	private long requestCount;
}
