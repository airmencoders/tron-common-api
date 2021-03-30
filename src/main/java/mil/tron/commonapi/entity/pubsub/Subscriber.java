package mil.tron.commonapi.entity.pubsub;

import lombok.*;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import mil.tron.commonapi.validations.ValidSubscriberAddress;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
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
    @NotNull
    @ValidSubscriberAddress
    private String subscriberAddress;

    @Getter
    @Setter
    @NotNull
    @Enumerated(EnumType.STRING)
    private EventType subscribedEvent;

    @Getter
    @Setter
    private String secret;

    @Override
    public String toString() {
        return String.format("Subscriber - %s :: %s", this.subscriberAddress, this.subscribedEvent);
    }
}
