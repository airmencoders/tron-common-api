package mil.tron.commonapi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mil.tron.commonapi.dto.response.pagination.Pagination;

/**
 * Wraps data response in envelope
 * 
 * @param <T> The type of the data being returned
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaginationResponse<T> {
	@Getter
	@Setter
	private T data;
	
	@Getter
	@Setter
	private Pagination pagination;
}
