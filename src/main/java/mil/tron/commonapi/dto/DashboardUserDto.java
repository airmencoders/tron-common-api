package mil.tron.commonapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import javax.persistence.Id;
import javax.validation.constraints.Email;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public class DashboardUserDto {
    @Id
    @Getter
    @Setter
    @Builder.Default
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id = UUID.randomUUID();

    @Email(regexp = ".+@.+\\..+$", message = "Malformed email address")
    @Getter
    @Setter
    private String email;

    @Getter
    @Setter
    @Builder.Default
    private List<PrivilegeDto> privileges = new ArrayList<>();
    
    @Getter
    @Setter
    private UUID defaultDocumentSpaceId;
}
