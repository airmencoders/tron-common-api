package mil.tron.commonapi.dto.documentspace;

import lombok.*;

import java.util.List;

/**
 * Used for deleting item(s) from the current path being viewed
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DocumentSpaceDeleteItemsDto {

    @Getter
    @Setter
    private String currentPath;

    @Getter
    @Setter
    private List<String> itemsToDelete;
}
