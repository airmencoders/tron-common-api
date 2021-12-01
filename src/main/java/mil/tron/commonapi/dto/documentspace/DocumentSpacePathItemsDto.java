package mil.tron.commonapi.dto.documentspace;

import lombok.*;

import java.util.List;

import javax.validation.constraints.NotNull;

/**
 * Used for path related entries
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DocumentSpacePathItemsDto {

    @Getter
    @Setter
    @NotNull
    private String currentPath;

    @Getter
    @Setter
    @NotNull
    private List<String> items;
}
