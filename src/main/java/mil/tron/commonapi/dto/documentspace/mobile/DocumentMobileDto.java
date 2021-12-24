package mil.tron.commonapi.dto.documentspace.mobile;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import mil.tron.commonapi.dto.documentspace.DocumentDto;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Data
public class DocumentMobileDto extends DocumentDto {

    @Getter
    private boolean isFavorite;

    @Getter
    private UUID elementUniqueId;
}
