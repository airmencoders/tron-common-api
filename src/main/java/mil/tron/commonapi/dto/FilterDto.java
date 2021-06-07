package mil.tron.commonapi.dto;

import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;
import mil.tron.commonapi.repository.filter.FilterCriteria;

public class FilterDto {
	@Getter
	@Setter
	@Valid
	@NotEmpty
	@NotNull
	private List<FilterCriteria> filterCriteria;
}
