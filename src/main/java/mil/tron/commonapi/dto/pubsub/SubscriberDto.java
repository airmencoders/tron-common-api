package mil.tron.commonapi.dto.pubsub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import mil.tron.commonapi.entity.pubsub.events.EventType;

import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Represents a pub sub subscribers record
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubscriberDto {

    @Builder.Default
    @Getter
    @Setter
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id = UUID.randomUUID();

    @Getter
    @Setter
    @NotNull
    private String appClientUser;

    @Getter
    @Setter
    private String secret;

    @Getter
    @Setter
    @NotNull
    private EventType subscribedEvent;

    @Getter
    @Setter
    private String subscriberAddress = "";

}
