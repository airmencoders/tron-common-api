package mil.tron.commonapi.entity.pubsub.events;

/**
 * The types of events an app can subscribe to.
 */
public enum EventType {
    PERSON_CHANGE,  // a new person was created or edited
    PERSON_DELETE,  // a person was deleted
    ORGANIZATION_CHANGE,  // an org was created or edited
    ORGANIZATION_DELETE,  // an org was deleted
    PERSON_ORG_ADD, // person was added to an organization
    PERSON_ORG_REMOVE, // person was removed from an organization
    SUB_ORG_ADD, // subordinate organization was added to an organization
    SUB_ORG_REMOVE // subordinate organization was removed from an organization
}
