package mil.tron.commonapi.dto.kpi;

import javax.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class ServiceMetricDto {
	@Getter
	@Setter
	@NotNull
	private String name;
	
	@Getter
	@Setter
	private Double averageLatency;
	
	@Getter
	@Setter
	@NotNull
	private Long responseCount;
}
