package mil.tron.commonapi.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
public class AppClientUserPrivDto {

    @Getter
    @Setter
    private UUID appClientUser;

    @Getter
    @Setter
    private List<Long> privilegeIds;
}
