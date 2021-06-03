package mil.tron.commonapi.entity.pubsub;

import lombok.*;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.pubsub.events.EventType;

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

    /**
     * The endpoint that gets added onto the app clients cluster local address
     */
    @Getter
    @Setter
    private String subscriberAddress;

    @Getter
    @Setter
    @NotNull
    @Enumerated(EnumType.STRING)
    private EventType subscribedEvent;

    @Getter
    @Setter
    private String secret;

    /**
     * The AppClient associated with this subscription
     */
    @Getter
    @Setter
    @NotNull
    @ManyToOne(fetch = FetchType.EAGER)
    private AppClientUser appClientUser;

    @Override
    public String toString() {
        return String.format("Subscriber - %s :: %s", this.subscriberAddress, this.subscribedEvent);
    }
}
