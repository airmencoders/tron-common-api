package mil.tron.commonapi.dto.documentspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class DocumentSpaceFolderInfoDto {

    @NotNull
    @Builder.Default
    private long size = 0L;

    @NotNull
    @Builder.Default
    private long count = 0L;

    @NotBlank
    @NotNull
    @Column(name="item_name", nullable = false)
    private String itemName;

    @Builder.Default
    @NotNull
    @Column(name="item_id", nullable = false)
    private UUID itemId = UUID.randomUUID();

    @NotNull
    @Column(name="doc_space_id", nullable = false)
    private UUID documentSpaceId;

}
