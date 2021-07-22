package mil.tron.commonapi.dto.kpi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import mil.tron.commonapi.entity.kpi.UserWithRequestCount;

@AllArgsConstructor
@Data
@Builder
public class UserWithRequestCountDto implements UserWithRequestCount {
	@Getter
	@Setter
	private String name;
	
	@Getter
	@Setter
	private long requestCount;
}
