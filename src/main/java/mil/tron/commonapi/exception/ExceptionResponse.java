package mil.tron.commonapi.exception;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class ExceptionResponse {

	@Getter
	private Date timestamp;

	@Getter
	private int status;

	@Getter
	private String error;

	@Getter
	private String reason;

	@Getter
	private String path;
}
