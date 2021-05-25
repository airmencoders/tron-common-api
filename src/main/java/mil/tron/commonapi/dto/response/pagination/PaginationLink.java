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
public class PaginationLink {
	@Getter
	@Setter
	private String next;
	
	@Getter
	@Setter
	private String last;
	
	@Getter
	@Setter
	private String prev;
	
	@Getter
	@Setter
	private String first;
}
