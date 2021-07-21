package mil.tron.commonapi.dto.kpi;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
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
