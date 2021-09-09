package mil.tron.commonapi.exception.custom;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
public class ValidationError {
	@Getter
	@Setter
	@NotNull
	private String defaultMessage;
	
	@Getter
	@Setter
	@NotNull
	private String objectName;
	
	@Getter
	@Setter
	@NotNull
	@JsonProperty("fieldName")
	private String field;
	
	@Getter
	@Setter
	@NotNull
	private Object rejectedValue;
	
	@Getter
	@Setter
	@NotNull
	private String code;
}
