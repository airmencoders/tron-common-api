package mil.tron.commonapi.dto.pubsub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import mil.tron.commonapi.entity.pubsub.events.EventType;

import java.util.Date;
import java.util.UUID;

/**
 * Represents a ledger entry from the pubsub ledger
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public class PubSubLedgerEntryDto {

    @Getter
    @Setter
    private UUID id = UUID.randomUUID();

    @Getter
    @Setter
    private Long countForEventType;

    @Getter
    @Setter
    private EventType eventType;

    @Getter
    @Setter
    private String data;

    @Getter
    @Setter
    private Date dateCreated;
}
