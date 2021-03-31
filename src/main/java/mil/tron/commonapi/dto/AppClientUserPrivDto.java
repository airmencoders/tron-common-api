package mil.tron.commonapi.dto;

import java.util.UUID;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
public class AppClientUserPrivDto {


    @NotNull
    private UUID appClientUser;
    
    @JsonInclude(Include.NON_NULL)
    private String appClientUserName;

    @NotNull
    private UUID appEndpoint;

    @JsonInclude(Include.NON_NULL)
    private String privilege;
    // @Getter
    // @Setter
    // @NotNull
    // @NotEmpty
    // private List<Long> privilegeIds;
}
