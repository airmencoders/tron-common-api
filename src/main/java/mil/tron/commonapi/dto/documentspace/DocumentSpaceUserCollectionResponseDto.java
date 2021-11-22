package mil.tron.commonapi.dto.documentspace;

import com.fasterxml.jackson.annotation.JsonFormat;
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
	private UUID itemId;

	@NotNull
	@Getter
	@Setter
	private UUID documentSpaceId;

	@NotNull
	@Getter
	@Setter
	private String key;
	
	@Getter
	@Setter
	private UUID parentId;

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
	@NotNull
	@Getter
	@Setter
	private Date lastModifiedDate;
	
	@Getter
	@Setter
	private boolean isFolder;
	
	@NotNull
	private DocumentMetadata metadata;
}
