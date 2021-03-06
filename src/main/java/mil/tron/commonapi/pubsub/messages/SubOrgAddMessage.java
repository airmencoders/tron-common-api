package mil.tron.commonapi.pubsub.messages;

import lombok.*;
import mil.tron.commonapi.entity.pubsub.events.EventType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a message that says what orgs were added to another org as subordinates
 * All orgs are represented by UUIDs
 */
public class SubOrgAddMessage extends PubSubMessage {

    public SubOrgAddMessage() {
        super();
        super.setEventType(EventType.SUB_ORG_ADD);
    }

    @Getter
    @Setter
    private UUID parentOrgId;

    @Getter
    @Setter
    @Builder.Default
    private Set<UUID> subOrgsAdded = new HashSet<>();

    public void addSubOrg(UUID subOrg) {
        this.subOrgsAdded.add(subOrg);
    }
}
