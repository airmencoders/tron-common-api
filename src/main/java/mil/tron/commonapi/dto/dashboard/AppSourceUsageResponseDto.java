package mil.tron.commonapi.dto.dashboard;

import java.util.Date;
import java.util.LinkedList;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Builder
public class AppSourceUsageResponseDto {
	@Getter
	@Setter
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
	@Schema(type="string", format = "date")
	private Date startDate;
	
	@Getter
	@Setter
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
	@Schema(type="string", format = "date")
	private Date endDate;
	
	@Getter
	@Setter
	LinkedList<AppSourceUsageDto> appSourceUsage;
}
