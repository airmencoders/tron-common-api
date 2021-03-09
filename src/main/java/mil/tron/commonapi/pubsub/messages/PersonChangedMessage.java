package mil.tron.commonapi.pubsub.messages;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import mil.tron.commonapi.entity.pubsub.events.EventType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a message that says what persons(s) were changed or created
 * All entities are represented by UUIDs
 */
public class PersonChangedMessage extends PubSubMessage {


    public PersonChangedMessage() {
        super();
        super.setEventType(EventType.PERSON_CHANGE);
    }

    @Getter
    @Setter
    @Builder.Default
    private Set<UUID> personIds = new HashSet<>();

    public void addPersonId(UUID person) {
        this.personIds.add(person);
    }

}