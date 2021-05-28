package mil.tron.commonapi.dto.pubsub;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
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
    private String secret;

    @JsonSetter
    public void setSecret(String secret) {
        this.secret = secret;
    }

    /**
     * Don't ever return/show the secret
     */
    @JsonIgnore
    public String getSecret() {
        return this.secret;
    }

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
