package mil.tron.commonapi.dto;

import lombok.*;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.Privilege;

import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
public class AppClientUserPrivDto {

    private UUID appClientUser;

    private List<Long> privilegeIds;
}
