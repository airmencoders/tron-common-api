package mil.tron.commonapi.pubsub.messages;

import lombok.*;
import mil.tron.commonapi.entity.pubsub.events.EventType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a message that says what persons(s) were added to another org as members
 * All entities are represented by UUIDs
 */
public class PersonOrgAddMessage extends PubSubMessage {

    public PersonOrgAddMessage() {
        super();
        super.setEventType(EventType.PERSON_ORG_ADD);
    }

    @Getter
    @Setter
    private UUID parentOrgId;

    @Getter
    @Setter
    @Builder.Default
    private Set<UUID> membersAdded = new HashSet<>();

    public void addMember(UUID member) {
        this.membersAdded.add(member);
    }
}