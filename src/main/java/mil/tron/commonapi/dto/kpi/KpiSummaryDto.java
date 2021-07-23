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
	private long averageLatencyForSuccessfulRequests;
	
	@Getter
	@Setter
	private long appSourceCount;
	
	@Getter
	@Setter
	private long appClientToAppSourceRequestCount;
	
	@Getter
	@Setter
	private UniqueVisitorSummaryDto uniqueVisitorySummary;
}
