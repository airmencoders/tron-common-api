package mil.tron.commonapi.dto.documentspace;

import lombok.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * Used for folder deletion
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DocumentSpacePathDto {

    @Getter
    @Setter
    @NotBlank
    @NotNull
    private String path;
}
