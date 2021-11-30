package mil.tron.commonapi.dto.documentspace;

import lombok.*;

import java.util.List;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;

/**
 * Used for path related entries
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DocumentSpacePathItemsDto {

    @Getter
    @Setter
    @NotBlank
    private String currentPath;

    @Getter
    @Setter
    @NotEmpty
    private List<String> items;
}
