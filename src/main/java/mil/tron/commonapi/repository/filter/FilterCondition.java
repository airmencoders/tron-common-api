package mil.tron.commonapi.repository.filter;

import java.util.List;

import javax.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import mil.tron.commonapi.validations.FilterConditionValidation;

@Builder
@FilterConditionValidation
public class FilterCondition {
	@Getter
	@Setter
	@NotNull
	@Schema(description = "The operation to perform for the condition")
	private QueryOperator operator;

	@Getter
	@Setter
	@Schema(description = "The value to perform the operation against")
	private String value;

	/**
	 * Used when using the IN query operator
	 */
	@Getter
	@Setter
	@Schema(description = "The values to perform the operation against (only used when operator set to IN)")
	private List<String> values;
}
