package mil.tron.commonapi.pubsub.messages;

import lombok.*;
import mil.tron.commonapi.entity.pubsub.events.EventType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a message that says what orgs were removed from another org as subordinates
 * All orgs are represented by UUIDs
 */
public class SubOrgRemoveMessage extends PubSubMessage {

    public SubOrgRemoveMessage() {
        super();
        super.setEventType(EventType.SUB_ORG_REMOVE);
    }

    @Getter
    @Setter
    private UUID parentOrgId;

    @Getter
    @Setter
    @Builder.Default
    private Set<UUID> subOrgsRemoved = new HashSet<>();

    public void addSubOrg(UUID subOrg) {
        this.subOrgsRemoved.add(subOrg);
    }
}