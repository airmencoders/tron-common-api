package mil.tron.commonapi.dto.documentspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class DocumentSpaceAppClientMemberRequestDto {

    @NotNull
    private UUID appClientId;

    @NotNull
    private List<ExternalDocumentSpacePrivilegeType> privileges;
}
