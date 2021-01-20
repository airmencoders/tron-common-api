package mil.tron.commonapi.pubsub.listeners;

import mil.tron.commonapi.entity.Airman;
import mil.tron.commonapi.entity.pubsub.events.EventTypes;
import mil.tron.commonapi.logging.CommonApiLogger;
import mil.tron.commonapi.pubsub.EventPublisher;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.persistence.PostPersist;
import javax.persistence.PostRemove;
import javax.persistence.PostUpdate;

/**
 * Listens for changes to {@link Airman} entities
 */
public class AirmanEntityListener {

    private static final Log APP_LOGGER = LogFactory.getLog(CommonApiLogger.class);
    private static final String AIRMAN_CHANGE_MSG = "Airman Data Changed";

    @Autowired
    private EventPublisher publisher;

    /**
     * Fires off a request to the EventPublisher to tell subscribers that something changed with Airman
     * @param airman The {@link Airman} entity that was added/changed/deleted
     */
    @PostPersist
    @PostUpdate
    @PostRemove
    public void afterAnyUpdate(Airman airman) {
        APP_LOGGER.info("[PUB-SUB EVENT] add/update/delete complete for Airman ID: " + airman.getId());
        publisher.publishEvent(EventTypes.AIRMAN_CHANGE, AIRMAN_CHANGE_MSG);
    }
}
