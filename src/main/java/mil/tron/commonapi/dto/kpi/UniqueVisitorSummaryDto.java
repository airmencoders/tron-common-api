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
	private long dashboardUserCount;
	
	@Getter
	@Setter
	private long dashboardUserRequestCount;
	
	@Getter
	@Setter
	private long appClientCount;
	
	@Getter
	@Setter
	private long appClientRequestCount;
}
