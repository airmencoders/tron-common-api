package mil.tron.commonapi.exception.custom;

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
	private String defaultMessage;
	
	@Getter
	@Setter
	private String objectName;
	
	@Getter
	@Setter
	@JsonProperty("fieldName")
	private String field;
	
	@Getter
	@Setter
	private Object rejectedValue;
	
	@Getter
	@Setter
	private String code;
}
