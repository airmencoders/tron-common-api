package mil.tron.commonapi.dto.documentspace;

import lombok.Builder;
import lombok.Data;
import mil.tron.commonapi.service.documentspace.DocumentSpacePrivilegeType;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

@Builder
@Data
public class DocumentSpaceAppClientResponseDto {

    @NotNull
    private UUID appClientId;

    @NotNull
    private String appClientName;

    @NotNull
    private List<DocumentSpacePrivilegeType> privileges;
}
