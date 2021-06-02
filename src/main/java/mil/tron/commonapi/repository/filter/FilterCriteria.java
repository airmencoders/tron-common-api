package mil.tron.commonapi.repository.filter;

import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
public class FilterCriteria {
	@Getter
	@Setter
	@Schema(description = "The relationship between the conditions. Will default to use AND if none provided. Has no effect if only one condition is given")
	private RelationType relationType;

	@Getter
	@Setter
	@NotNull
	@Schema(description = "the name of the field to compare against")
	private String field;

	@Getter
	@Setter
	@JsonIgnore
	private String joinAttribute;

	@Getter
	@Setter
	@NotEmpty
	@Valid
	@Schema(description = "The list of conditions that must be fulfilled")
	private List<FilterCondition> conditions;
}
