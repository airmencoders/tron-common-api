package mil.tron.commonapi.exception;

import java.util.Date;
import java.util.List;

import javax.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import mil.tron.commonapi.exception.custom.ValidationError;

@AllArgsConstructor
public class ExceptionResponse {

	@Getter
	@NotNull
	private Date timestamp;

	@Getter
	@NotNull
	private int status;

	@Getter
	@NotNull
	private String error;

	@Getter
	@NotNull
	private String reason;

	@Getter
	@NotNull
	private String path;
	
	@Getter
	@Schema(nullable = true,
			description = "Field will only exist if there are validation errors")
	private List<ValidationError> errors;
}
