package mil.tron.commonapi.dto.dashboard;

import java.util.Date;
import java.util.List;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Builder
public class EntityAccessorResponseDto implements DashboardDateRange {
	@Getter
	@Setter
	private Date startDate;
	
	@Getter
	@Setter
	private Date endDate;
	
	@Getter
	@Setter
	List<EntityAccessorDto> entityAccessors;
}
