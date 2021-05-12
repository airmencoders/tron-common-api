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
@Getter
@Setter
@Builder
public class PaginationResponse<T> {
	private T data;
	private Pagination pagination;
}
