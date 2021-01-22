package mil.tron.commonapi.pubsub.listeners;

import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import mil.tron.commonapi.logging.CommonApiLogger;
import mil.tron.commonapi.pubsub.EventPublisher;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.persistence.PostPersist;
import javax.persistence.PostRemove;
import javax.persistence.PostUpdate;

/**
 * Listens for changes to {@link Person} entities
 */
public class PersonEntityListener {

    private static final Log APP_LOGGER = LogFactory.getLog(CommonApiLogger.class);
    private static final String PERSON_CHANGE_MSG = "Person Data Changed";
    private static final String PERSON_CREATE_MSG = "Person Created";
    private static final String PERSON_DELETE_MSG = "Person Removed";

    @Autowired
    private EventPublisher publisher;

    /**
     * Fires off a request to the EventPublisher to tell subscribers that something changed with Person
     * @param person The {@link Person} entity that was added
     */
    @PostPersist
    public void afterAnyCreation(Person person) {
        APP_LOGGER.info("[PUB-SUB EVENT] add complete for Person ID: " + person.getId());
        publisher.publishEvent(
                EventType.PERSON_CREATE,
                PERSON_CREATE_MSG, 
                person.getClass().getName(),
                person);
    }

    /**
     * Fires off a request to the EventPublisher to tell subscribers that something changed with Person
     * @param person The {@link Person} entity that was changed
     */
    @PostUpdate
    public void afterAnyUpdate(Person person) {
        APP_LOGGER.info("[PUB-SUB EVENT] add/update/delete complete for Person ID: " + person.getId());
        publisher.publishEvent(
                EventType.PERSON_CHANGE,
                PERSON_CHANGE_MSG,
                person.getClass().getName(),
                person);
    }

    /**
     * Fires off a request to the EventPublisher to tell subscribers that something changed with Person
     * @param person The {@link Person} entity that was deleted
     */
    @PostRemove
    public void afterAnyRemoval(Person person) {
        APP_LOGGER.info("[PUB-SUB EVENT] delete complete for Person ID: " + person.getId());
        publisher.publishEvent(
                EventType.PERSON_DELETE, 
                PERSON_DELETE_MSG,
                person.getClass().getName(),
                person);
    }
}
