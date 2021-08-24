package mil.tron.commonapi.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mil.tron.commonapi.entity.dashboard.EntityAccessor;

@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntityAccessorDto implements EntityAccessor {
	@Getter
	@Setter
	private String name;
	
	@Getter
	@Setter
	private Long recordAccessCount;
}
