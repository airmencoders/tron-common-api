package mil.tron.commonapi.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
public class AppClientUserPrivDto {

    @Getter
    @Setter
    @NotNull
    private UUID appClientUser;
    
    @Getter
    @Setter
    @JsonInclude(Include.NON_NULL)
    private String appClientUserName;

    @Getter
    @Setter
    @NotNull
    @NotEmpty
    private List<Long> privilegeIds;
}
