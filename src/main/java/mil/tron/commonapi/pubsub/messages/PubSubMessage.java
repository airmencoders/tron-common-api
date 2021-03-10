package mil.tron.commonapi.pubsub.messages;

import lombok.*;
import mil.tron.commonapi.entity.pubsub.events.EventType;

/**
 * Base class for all pub sub messages to inherit from
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
public class PubSubMessage {

    @Getter
    @Setter
    private EventType eventType;

    /**
     * Event count for this type of event
     */
    @Getter
    @Setter
    private Long eventCount;
}
