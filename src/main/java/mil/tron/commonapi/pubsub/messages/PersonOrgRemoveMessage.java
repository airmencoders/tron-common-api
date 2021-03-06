package mil.tron.commonapi.pubsub.messages;

import lombok.*;
import mil.tron.commonapi.entity.pubsub.events.EventType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a message that says what persons(s) were removed from another org as members
 * All entities are represented by UUIDs
 */
public class PersonOrgRemoveMessage extends PubSubMessage {

    public PersonOrgRemoveMessage() {
        super();
        super.setEventType(EventType.PERSON_ORG_REMOVE);
    }

    @Getter
    @Setter
    private UUID parentOrgId;

    @Getter
    @Setter
    @Builder.Default
    private Set<UUID> memberRemoved = new HashSet<>();

    public void addMember(UUID member) {
        this.memberRemoved.add(member);
    }
}