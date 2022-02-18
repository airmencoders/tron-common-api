package mil.tron.commonapi.dto.documentspace;


import java.util.Date;
import java.util.UUID;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@EqualsAndHashCode
public class RecentDocumentDto {
	public RecentDocumentDto(@NotNull UUID id, @NotNull String key, @NotNull UUID parentFolderId, @NotNull Date lastModifiedDate,
			String lastActivityBy, @NotNull UUID documentSpaceId, @NotNull String documentSpaceName) {
		super();
		this.id = id;
		this.key = key;
		this.lastActivityBy = lastActivityBy;
		this.parentFolderId = parentFolderId;
		this.lastModifiedDate = lastModifiedDate;
		this.documentSpace = new DocumentSpaceResponseDto(documentSpaceId, documentSpaceName);
	}

	@NotNull
	private UUID id;
	
	@NotNull
	private String key;

	private String lastActivityBy = "Unknown";
	
	@NotNull
	private UUID parentFolderId;

	/**
	 * This field becomes the last activity date, or last modified date, or the creation date (which
	 * ever is non-null first) as determined in the JPQL query
	 */
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
	@NotNull
	private Date lastModifiedDate;

	@NotNull
	private DocumentSpaceResponseDto documentSpace;
}
