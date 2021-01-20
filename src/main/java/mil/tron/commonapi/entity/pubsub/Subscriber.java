package mil.tron.commonapi.entity.pubsub;

import lombok.*;
import mil.tron.commonapi.entity.pubsub.events.EventTypes;

import javax.persistence.*;
import java.util.UUID;

/**
 * Represents a single event subscription entry
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table
@EqualsAndHashCode
public class Subscriber {

    @Id
    @Builder.Default
    @Getter
    @Setter
    private UUID id = UUID.randomUUID();

    @Getter
    @Setter
    private String subscriberAddress;

    @Getter
    @Setter
    @Enumerated(EnumType.STRING)
    private EventTypes subscribedEvent;

    @Override
    public String toString() {
        return String.format("Subscriber - %s :: %s", this.subscriberAddress, this.subscribedEvent);
    }
}
