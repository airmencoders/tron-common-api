package mil.tron.commonapi.dto.documentspace;

import lombok.*;

import java.util.List;

/**
 * Used for un-archiving item(s)
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DocumentSpaceUnArchiveItemsDto {

    @Getter
    @Setter
    private List<String> itemsToUnArchive;
}
