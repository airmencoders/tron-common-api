package mil.tron.commonapi.pubsub;

import com.google.common.collect.Lists;
import mil.tron.commonapi.entity.pubsub.Subscriber;
import mil.tron.commonapi.entity.pubsub.events.EventTypes;
import mil.tron.commonapi.logging.CommonApiLogger;
import mil.tron.commonapi.service.pubsub.SubscriberService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Service that fires off messages to subscribers for various events.  Entity listeners
 * call {@link EventPublisher#publishEvent(EventTypes, String)} to launch an asynchronous
 * broadcast to subscribers to the provided event type.
 */
@Service
public class EventPublisher {
    private final Log publisherLog = LogFactory.getLog(CommonApiLogger.class);

    @Autowired
    private SubscriberService subService;

    @Autowired
    private RestTemplate publisherSender;

    /**
     * Called by Entity Listeners for various types of JPA persistence changes.  Non-blocking operation.
     * @param type {@link EventTypes} type event to broadcast
     * @param message String message to send to subscribers
     */
    @Async
    public void publishEvent(EventTypes type, String message) {
        List<Subscriber> subscribers = Lists.newArrayList(subService.getSubscriptionsByEventType(type));
        for (Subscriber s : subscribers) {
            publisherLog.info("[PUBLISH BROADCAST] - Event: " + type.toString() + " Message: " + message + " to Subscriber: " + s.getSubscriberAddress());
            try {
                publisherSender.getForEntity(s.getSubscriberAddress(), String.class);
            }
            catch (RestClientException e) {
                publisherLog.warn("[PUBLISH ERROR] - Subscriber: " + s.getSubscriberAddress() + " failed.  Exception: " + e.getMessage());
            }
        }
    }
}
