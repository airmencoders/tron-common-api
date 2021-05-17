package mil.tron.commonapi.dto.response.pagination;

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
	private T data;
	
	@Getter
	@Setter
	private Pagination pagination;
}
