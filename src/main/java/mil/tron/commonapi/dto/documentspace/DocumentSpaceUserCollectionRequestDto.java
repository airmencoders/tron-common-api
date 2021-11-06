package mil.tron.commonapi.dto.documentspace;

import lombok.*;

import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 *  Used for documentSpaceUserCollection write requests
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public class DocumentSpaceUserCollectionRequestDto {
    @NotNull
    private UUID documentSpaceId;

    @NotNull
    private UUID dashboardUserId;

    private String name;
}
