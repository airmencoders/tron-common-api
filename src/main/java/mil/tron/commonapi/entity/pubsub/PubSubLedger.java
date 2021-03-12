package mil.tron.commonapi.entity.pubsub;


import lombok.*;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.util.Date;
import java.util.UUID;

/**
 * The proverbial ledger that records all transactions in Common API person/orgs
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@EqualsAndHashCode
public class PubSubLedger {

    @Id
    @Getter
    @Setter
    private UUID id = UUID.randomUUID();

    @Getter
    @Setter
    private Long countForEventType;

    /**
     * The type of change
     */
    @Getter
    @Setter
    @Enumerated(value = EnumType.STRING)
    private EventType eventType;

    /**
     * The serialized version of the PubSubMessage that is broadcast
     */
    @Getter
    @Setter
    @Column(length = 2097152)
    private String data;

    @CreationTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    @Getter
    private Date dateCreated;
}
