package mil.tron.commonapi.pubsub.messages;

import lombok.*;
import mil.tron.commonapi.entity.pubsub.events.EventType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a message that says what organizations(s) were changed or created
 * All entities are represented by UUIDs
 */
public class OrganizationChangedMessage extends PubSubMessage {

    public OrganizationChangedMessage() {
        super();
        super.setEventType(EventType.ORGANIZATION_CHANGE);
    }

    @Getter
    @Setter
    @Builder.Default
    private Set<UUID> orgIds = new HashSet<>();

    public void addOrgId(UUID orgId) {
        this.orgIds.add(orgId);
    }

}