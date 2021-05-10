package mil.tron.commonapi.pubsub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.dto.EventInfoDto;
import mil.tron.commonapi.dto.pubsub.PubSubLedgerEntryDto;
import mil.tron.commonapi.entity.pubsub.PubSubLedger;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.logging.CommonApiLogger;
import mil.tron.commonapi.pubsub.messages.PubSubMessage;
import mil.tron.commonapi.repository.pubsub.PubSubLedgerRepository;
import mil.tron.commonapi.security.AppClientPreAuthFilter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.assertj.core.util.Lists;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

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
    private ModelMapper mapper = new ModelMapper();

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
    public Iterable<PubSubLedgerEntryDto> getMessagesSinceDateTime(Date timeDateStamp) {
        return Lists.newArrayList(pubSubLedgerRepository
                .findByDateCreatedGreaterThan(timeDateStamp))
                .stream()
                .map(item -> mapper.map(item, PubSubLedgerEntryDto.class))
                .collect(Collectors.toList());
    }

    /**
     * Gets the latest counts from the database for the various PubSub messages
     * @return a List of the counts with the event names being the keys
     */
    @Override
    public Iterable<EventInfoDto> getEventTypeCounts() {
        List<EventInfoDto> counts = new ArrayList<>();

        for (EventType event: EventType.values()) {
            counts.add(EventInfoDto.builder()
                    .eventType(event)
                    .eventCount(pubSubLedgerRepository.countByEventType(event))
                    .build());
        }

        return counts;
    }

    /**
     * Gets all ledger messages since given event count for a given list of event types and their respective counts.
     * Start of the return list will begin at the oldest of the event type/event count combination found in the input "events"
     * list.
     * @param events array/list of EventInfoDto objects
     * @return List of ledger entries containing only the supplied event types
     */
    @Override
    public Iterable<PubSubLedgerEntryDto> getMessagesSinceEventCountByType(List<EventInfoDto> events) {

        // for each of the objects (EventInfoDto) given in the input list, find their date/time in the ledger
        //  so we can find which is the oldest later on
        List<PubSubLedgerEntryDto> entries = new ArrayList<>();
        Map<EventType, Long> eventsMap = new HashMap<>();
        for (EventInfoDto dto : events) {
            if (dto.getEventCount() < 0L)
                throw new BadRequestException("Event Count cannot be less than zero");

            eventsMap.put(dto.getEventType(), dto.getEventCount());
            pubSubLedgerRepository
                    .findByEventTypeEqualsAndCountForEventTypeEquals(dto.getEventType(), dto.getEventCount() + 1L) // incr count by one to get next one
                    .ifPresent(item -> entries.add(mapper.map(item, PubSubLedgerEntryDto.class)));
        }

        if (entries.isEmpty())
            return entries;

        // now find the oldest eventType/eventCount combo
        Date oldestDate = entries.get(0).getDateCreated();
        for (int i = 1; i< entries.size(); i++) {
            if (entries.get(i).getDateCreated().compareTo(oldestDate) < 0) {
                // found new oldest date
                oldestDate = entries.get(i).getDateCreated();
            }
        }

        // now find, from the oldest date, the entries that's happened since (probably will contain events we don't want, but filter later)
        List<PubSubLedger> recalledEvents = Lists.newArrayList(pubSubLedgerRepository.findByDateCreatedGreaterThanEqual(oldestDate));

        // now filter out only events we want at the events counts > than the counts given for that event type
        return recalledEvents
                .stream()
                .filter(item -> eventsMap.containsKey(item.getEventType())
                        && item.getCountForEventType() > eventsMap.get(item.getEventType()))
                .collect(Collectors.toList())
                    .stream()
                    .map(item -> mapper.map(item, PubSubLedgerEntryDto.class))
                    .collect(Collectors.toList());
    }

}
