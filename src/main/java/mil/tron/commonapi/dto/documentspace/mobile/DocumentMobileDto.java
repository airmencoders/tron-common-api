package mil.tron.commonapi.dto.documentspace.mobile;

import lombok.*;
import lombok.experimental.SuperBuilder;
import mil.tron.commonapi.dto.documentspace.DocumentDto;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class DocumentMobileDto extends DocumentDto {

    @Getter
    private boolean isFavorite;

    @Getter
    private UUID elementUniqueId;

    @Getter
    private UUID parentId;
}
