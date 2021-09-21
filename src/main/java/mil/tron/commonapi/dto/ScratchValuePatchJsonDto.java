package mil.tron.commonapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import javax.validation.constraints.NotBlank;

/**
 * A DTO to carry a value and a json path string to update/set a field
 * in a scratch space value that is being treated as JSON
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScratchValuePatchJsonDto {

    @Getter
    @Setter
    @NotBlank
    private String jsonPath;

    @Getter
    @Setter
    private String value;

    @Getter
    @Setter
    private String newFieldName;

    @Getter
    @Setter
    private boolean newEntry;
}
