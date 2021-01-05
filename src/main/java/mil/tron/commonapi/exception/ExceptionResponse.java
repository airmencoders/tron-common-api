package mil.tron.commonapi.exception;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ExceptionResponse {
	private Date timestamp;
	private int status;
	private String error;
	private String message;
	private String path;
}
