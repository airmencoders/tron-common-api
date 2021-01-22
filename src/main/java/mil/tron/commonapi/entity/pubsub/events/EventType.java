package mil.tron.commonapi.entity.pubsub.events;

/**
 * The types of events an app can subscribe to
 */
public enum EventType {
    PERSON_CREATE,
    PERSON_CHANGE,
    PERSON_DELETE,
    ORGANIZATION_CREATE,
    ORGANIZATION_CHANGE,
    ORGANIZATION_DELETE,
}
