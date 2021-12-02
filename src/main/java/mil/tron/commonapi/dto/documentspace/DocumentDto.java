package mil.tron.commonapi.dto.documentspace;

import java.util.Date;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Data
public class DocumentDto {
	@NotNull
	@Getter
	@Setter
	private String key;
	
	@NotNull
	@Getter
	@Setter
	private String path;

	@NotNull
	@Getter
	@Setter
	private String spaceId;

	@Getter
	@Setter
	private String spaceName;
	
	@NotNull
	@Getter
	@Setter
	@Schema(description="Size in bytes")
	private long size;
	
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
	@NotNull
	@Getter
	@Setter
	private Date lastModifiedDate;
	
	@NotNull
	@Getter
	@Setter
	private String lastModifiedBy;

	@Getter
	@Setter
	private boolean isFolder;

	@Getter
	@Setter
	private boolean hasContents;

}
