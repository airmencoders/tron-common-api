package mil.tron.commonapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import mil.tron.commonapi.entity.pubsub.events.EventType;


/**
 * DTO for holding the event count for a given pub sub message
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventInfoDto {

    @Getter
    @Setter
    private EventType eventType;

    @Getter
    @Setter
    private Long eventCount;
}
