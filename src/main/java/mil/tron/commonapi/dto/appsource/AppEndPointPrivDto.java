package mil.tron.commonapi.dto.appsource;

import lombok.*;

import java.util.UUID;

/**
 * DTO to setup a new app source to app client to endpoint relationship / privilege
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
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
