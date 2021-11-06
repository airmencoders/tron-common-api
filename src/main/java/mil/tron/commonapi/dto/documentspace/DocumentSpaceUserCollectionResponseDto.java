package mil.tron.commonapi.dto.documentspace;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;
import java.util.Date;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Data
public class DocumentSpaceUserCollectionResponseDto {

	@NotNull
	@Getter
	@Setter
	private UUID id;

	@NotNull
	@Getter
	@Setter
	private String key;
	
	@Getter
	@Setter
	private UUID parentId;

	@NotNull
	@Getter
	@Setter
	private UUID spaceId;
	
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
	@NotNull
	@Getter
	@Setter
	private Date lastModifiedDate;
	
	@Getter
	@Setter
	private boolean isFolder;
}
