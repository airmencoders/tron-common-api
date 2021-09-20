package mil.tron.commonapi.dto.documentspace;

import java.util.Date;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonFormat;

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
	
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
	@NotNull
	@Getter
	@Setter
	private Date uploadedDate;
	
	@NotNull
	@Getter
	@Setter
	private String uploadedBy;
}
