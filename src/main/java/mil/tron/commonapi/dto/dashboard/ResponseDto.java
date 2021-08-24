package mil.tron.commonapi.dto.dashboard;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Builder
public class ResponseDto {
	@Getter
	@Setter
	private int statusCode;
	
	@Getter
	@Setter
	private long count;
}
