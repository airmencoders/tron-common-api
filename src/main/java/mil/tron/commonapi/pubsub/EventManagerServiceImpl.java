package mil.tron.commonapi.pubsub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.entity.pubsub.PubSubLedger;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.pubsub.messages.PubSubMessage;
import mil.tron.commonapi.logging.CommonApiLogger;
import mil.tron.commonapi.repository.pubsub.PubSubLedgerRepository;
import mil.tron.commonapi.security.AppClientPreAuthFilter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * This service manages the launching of the pubsub events, its methods
 * are called from the various services that send out pubsub messages.
 *
 * This service will then use {@link EventPublisher} class to launch the asynchronous sending
 * of the messages.
 */
@Service
public class EventManagerServiceImpl implements EventManagerService {

    private static final Log APP_LOGGER = LogFactory.getLog(CommonApiLogger.class);
    private static final String LEDGER_ERROR = "Error pushing changes to pub-sub ledger";

    @Autowired
    private PubSubLedgerRepository pubSubLedgerRepository;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private HttpServletRequest servletRequest;

    private ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Records a pub-sub event to the ledger and passes the message on to the EventPublisher for broadcast
     * @param message The object (message) to broadcast
     */
    @Override
    public void recordEventAndPublish(PubSubMessage message) {

        String messageString;
        if (message == null) throw new BadRequestException(LEDGER_ERROR);

        Long eventCount = pubSubLedgerRepository.countByEventType(message.getEventType());
        // set the event count in the outgoing message
        message.setEventCount(eventCount+1);

        // serialize the message for the ledger
        try {
            messageString = objectMapper.writeValueAsString(message);
        }
        catch (JsonProcessingException e) {
            APP_LOGGER.info("[PUB-SUB EVENT] exception occurred serializing message for ledger: " + e.getMessage());
            throw new BadRequestException(LEDGER_ERROR);
        }

        // make the entry in the ledger for this event(s)
        //  each ledger entry has a flattened string of the message body sent out
        PubSubLedger entry = PubSubLedger
                .builder()
                .id(UUID.randomUUID())
                .eventType(message.getEventType())
                .data(messageString)
                .build();

        // set the event count before persisting
        entry.setCountForEventType(eventCount+1);

        // persist
        pubSubLedgerRepository.save(entry);


        // send it off to be broadcast async'ly by the Event Publisher
        APP_LOGGER.info("[PUB-SUB EVENT] add complete for Person ID(s): " + messageString);
        eventPublisher.publishEvent(message, servletRequest.getHeader(AppClientPreAuthFilter.XFCC_HEADER_NAME));
    }

    /**
     * Gets all ledger messages since given date time stamp
     * @param timeDateStamp  the date time stamp to start retrieving records from
     * @return List of ledger entries
     */
    @Override
    public Iterable<PubSubLedger> getMessagesSinceDateTime(Date timeDateStamp) {
        return pubSubLedgerRepository.findByDateCreatedGreaterThan(timeDateStamp);
    }

    /**
     * Gets the latest counts from the database for the various PubSub messages
     * @return a Map of the counts with the event names being the keys
     */
    @Override
    public Map<String, Long> getEventTypeCounts() {
        Map<String, Long> counts = new HashMap<>();

        for (EventType event: EventType.values()) {
            counts.put(event.toString(), pubSubLedgerRepository.countByEventType(event));
        }

        return counts;
    }

}
