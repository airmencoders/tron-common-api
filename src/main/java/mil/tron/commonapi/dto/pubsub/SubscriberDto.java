package mil.tron.commonapi.dto.pubsub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import mil.tron.commonapi.validations.ValidSubscriberAddress;

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
    private UUID id = UUID.randomUUID();

    @Getter
    @Setter
    private String secret;

    @Getter
    @Setter
    @NotNull
    private EventType subscribedEvent;

    @Getter
    @Setter
    @NotNull
    @ValidSubscriberAddress
    private String subscriberAddress;

}
