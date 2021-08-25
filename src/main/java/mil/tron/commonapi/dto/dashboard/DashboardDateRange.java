package mil.tron.commonapi.dto.dashboard;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

public interface DashboardDateRange {
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
	Date getStartDate();
	
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
	Date getEndDate();
}
