package mil.tron.commonapi.dto.documentspace;

import java.util.List;

import javax.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class S3PaginationDto {
	@Getter
	@Setter
	@NotNull
	List<DocumentDto> documents;
	
	@Getter
	@Setter
	String currentContinuationToken;
	
	@Getter
	@Setter
	String nextContinuationToken;
	
	@Getter
	@Setter
	@NotNull
	@Schema(description = "The size of the page")
	int size;
	
	@Getter
	@Setter
	@NotNull
	@Schema(description = "The size of the returned elements of this page")
	int totalElements;
}
