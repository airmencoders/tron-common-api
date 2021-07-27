package mil.tron.commonapi.dto.kpi;


import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Builder
public class UniqueVisitorSummaryDto {
	@Getter
	@Setter
	private Long dashboardUserCount;
	
	@Getter
	@Setter
	private Long dashboardUserRequestCount;
	
	@Getter
	@Setter
	private Long appClientCount;
	
	@Getter
	@Setter
	private Long appClientRequestCount;
}
