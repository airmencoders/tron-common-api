package mil.tron.commonapi.dto.documentspace;


import java.util.UUID;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
 * Information about a document space itself
 */

@AllArgsConstructor
@Getter
@Setter
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentSpaceInfoDto {
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private UUID id;
	
    @NotBlank
    @Size(max = 255)
    private String name;
}
