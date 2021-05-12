package mil.tron.commonapi.dto.response.pagination;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(Include.NON_NULL)
public class Pagination {
	@Getter
	@Setter
	private int page;
	
	@Getter
	@Setter
	private int size;
	
	@Getter
	@Setter
	private Long totalElements;
	
	@Getter
	@Setter
	private Integer totalPages;
	
	@Getter
	@Setter
	private PaginationLink links;
}
