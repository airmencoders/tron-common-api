package mil.tron.commonapi.pubsub.messages;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import mil.tron.commonapi.entity.pubsub.events.EventType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a message that says what persons(s) was deleted
 * All entities are represented by UUIDs
 */
public class PersonDeleteMessage extends PubSubMessage {

    public PersonDeleteMessage() {
        super();
        super.setEventType(EventType.PERSON_DELETE);
    }

    @Getter
    @Setter
    @Builder.Default
    private Set<UUID> personIds = new HashSet<>();

    public void addPersonId(UUID person) {
        this.personIds.add(person);
    }
}