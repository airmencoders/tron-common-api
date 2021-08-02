package mil.tron.commonapi.exception;

import java.util.Date;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import mil.tron.commonapi.exception.custom.ValidationError;

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
	
	@Getter
	@Schema(description = "Field will only exist if there are validation errors")
	private List<ValidationError> errors;
}
