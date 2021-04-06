package mil.tron.commonapi.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.*;

import javax.validation.constraints.NotNull;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
public class AppClientUserPrivDto {

    @Getter
    private UUID id;

    @NotNull
    private UUID appClientUser;
    
    @JsonInclude(Include.NON_NULL)
    private String appClientUserName;

    @NotNull
    private UUID appEndpoint;

    @JsonInclude(Include.NON_NULL)
    private String privilege;
}
