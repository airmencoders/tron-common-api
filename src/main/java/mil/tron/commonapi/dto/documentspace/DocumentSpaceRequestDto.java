package mil.tron.commonapi.dto.documentspace;


import java.util.UUID;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
 * DocumentSpace DTO for write operations
 *
 */
@AllArgsConstructor
@Getter
@Setter
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentSpaceRequestDto {
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private UUID id;
	
    @NotBlank
    @Size(max = 255)
    private String name;

    // trim whitespace
    public void setName(String name) {
        if (name != null) {
            this.name = name.trim();
        } else {
            this.name = null;
        }
    }


}
