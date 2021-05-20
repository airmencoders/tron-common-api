package mil.tron.commonapi.dto.response.pagination;

import javax.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Wraps data response in pagination envelope
 * 
 * @param <T> The type of the data being returned
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaginationWrappedResponse<T> {
	@Getter
	@Setter
	@NotNull
	private T data;
	
	@Getter
	@Setter
	@NotNull
	private Pagination pagination;
}
