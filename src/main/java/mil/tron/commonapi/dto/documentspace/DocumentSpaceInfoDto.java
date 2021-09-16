package mil.tron.commonapi.dto.documentspace;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import mil.tron.commonapi.validations.ValidDocumentSpaceName;

/**
 * Information about a document space itself
 */

@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public class DocumentSpaceInfoDto {

    @Getter
    @Setter
    @ValidDocumentSpaceName
    private String name;
}
