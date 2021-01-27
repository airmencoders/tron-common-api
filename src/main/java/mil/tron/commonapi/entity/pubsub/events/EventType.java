package mil.tron.commonapi.entity.pubsub.events;

/**
 * The types of events an app can subscribe to.  Note that these are for the base class
 * of the entity types... the base class of an entity will cover any changes to the subclasses
 * as well (e.g. Person fires events on Airman, etc)...
 */
public enum EventType {
    PERSON_CREATE,
    PERSON_CHANGE,
    PERSON_DELETE,
    ORGANIZATION_CREATE,
    ORGANIZATION_CHANGE,
    ORGANIZATION_DELETE,
}
