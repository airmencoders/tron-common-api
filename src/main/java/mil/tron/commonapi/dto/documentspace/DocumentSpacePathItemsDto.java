package mil.tron.commonapi.dto.documentspace;

import lombok.*;

import java.util.List;

/**
 * Used for path related entries
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DocumentSpacePathItemsDto {

    @Getter
    @Setter
    private String currentPath;

    @Getter
    @Setter
    private List<String> items;
}
