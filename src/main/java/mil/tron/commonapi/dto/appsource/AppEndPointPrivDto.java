package mil.tron.commonapi.dto.appsource;

import lombok.*;
import mil.tron.commonapi.validations.AppsMatch;

import java.util.UUID;

/**
 * DTO to setup a new app source to app client to endpoint relationship / privilege
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
@AppsMatch(invert = true, field = "appClientUserId", fieldMatch = "appSourceId", message="App cannot fetch from itself")
public class AppEndPointPrivDto {

    @Getter
    @Setter
    private UUID appSourceId;

    @Getter
    @Setter
    private UUID appEndpointId;

    @Getter
    @Setter
    private UUID appClientUserId;

}
