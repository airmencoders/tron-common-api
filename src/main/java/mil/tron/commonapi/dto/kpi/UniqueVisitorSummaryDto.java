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
