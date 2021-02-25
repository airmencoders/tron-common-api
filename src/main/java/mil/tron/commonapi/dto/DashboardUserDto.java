package mil.tron.commonapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import mil.tron.commonapi.entity.Privilege;

import javax.persistence.Id;
import javax.validation.constraints.Email;
import java.util.*;

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
    private UUID id = UUID.randomUUID();

    @Email(message="Malformed email address")
    @Getter
    @Setter
    private String email;

    @Getter
    @Setter
    @Builder.Default
    private List<Privilege> privileges = new ArrayList<>();
}
