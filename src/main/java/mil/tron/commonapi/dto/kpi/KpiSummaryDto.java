package mil.tron.commonapi.dto.kpi;


import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Builder
public class KpiSummaryDto {
	@Getter
	@Setter
	private Long averageLatencyForSuccessfulRequests;
	
	@Getter
	@Setter
	private Long appSourceCount;
	
	@Getter
	@Setter
	private Long appClientToAppSourceRequestCount;
	
	@Getter
	@Setter
	private UniqueVisitorSummaryDto uniqueVisitorySummary;
}
