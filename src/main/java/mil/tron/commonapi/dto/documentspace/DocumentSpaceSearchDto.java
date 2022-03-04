package mil.tron.commonapi.dto.documentspace;

import lombok.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * Used for sending search query to document space
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DocumentSpaceSearchDto {

    @Getter
    @Setter
    @NotNull
    @NotBlank
    private String query;
}
