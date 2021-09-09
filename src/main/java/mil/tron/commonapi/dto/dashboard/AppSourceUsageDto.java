package mil.tron.commonapi.dto.dashboard;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Builder
public class AppSourceUsageDto {
	@Getter
	@Setter
	private String name;
	
	@Getter
	@Setter
	private Long incomingRequestCount;
}
