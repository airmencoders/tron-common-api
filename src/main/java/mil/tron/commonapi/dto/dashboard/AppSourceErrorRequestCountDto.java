package mil.tron.commonapi.dto.dashboard;

import java.util.List;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Builder
public class AppSourceErrorRequestCountDto {
	@Getter
	@Setter
	private String name;
	
	@Getter
	@Setter
	private List<AppSourceErrorResponseDto> errorResponses;
}
