package mil.tron.commonapi.pubsub;

import com.google.common.collect.Lists;
import mil.tron.commonapi.entity.pubsub.Subscriber;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import mil.tron.commonapi.logging.CommonApiLogger;
import mil.tron.commonapi.service.pubsub.SubscriberService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service that fires off messages to subscribers for various events.  Entity listeners
 * call {@link EventPublisher#publishEvent(EventType, String, String, Object)} to launch an asynchronous
 * broadcast to subscribers to the provided event type.
 */
@Service
public class EventPublisher {
    private final Log publisherLog = LogFactory.getLog(CommonApiLogger.class);

    /**
     * Publisher REST bean that will timeout after 5secs to a subscriber so that
     * a subscriber can't block/hang the publisher thread
     * @param builder
     * @return RestTemplate for use by the EventPublisher
     */
    @Bean("eventSender")
    public RestTemplate publisherSender(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5L))
                .setReadTimeout(Duration.ofSeconds(5L))
                .build();
    }

    private SubscriberService subService;

    @Autowired
    @Qualifier("eventSender")
    private RestTemplate publisherSender;

    public EventPublisher(SubscriberService subService) {
        this.subService = subService;
    }

    /**
     * Called by Entity Listeners for various types of JPA persistence changes.  Non-blocking operation.
     * @param type {@link EventType} type event to broadcast
     * @param message String message to send to subscribers
     */
    @Async
    public void publishEvent(EventType type, String message, String className, Object data) {
        List<Subscriber> subscribers = Lists.newArrayList(subService.getSubscriptionsByEventType(type));

        Map<String, Object> messageDetails = new HashMap<>();
        messageDetails.put("event", type.toString());
        messageDetails.put("type", className);
        messageDetails.put("message", message);
        messageDetails.put("data", data);
        System.out.println("TEST");
        for (Subscriber s : subscribers) {
            publisherLog.info("[PUBLISH BROADCAST] - Event: " + type.toString() + " Message: " + message + " to Subscriber: " + s.getSubscriberAddress());
            try {
                publisherSender.postForLocation(s.getSubscriberAddress(), messageDetails);
                publisherLog.info("[PUBLISH SUCCESS] - Subscriber: " + s.getSubscriberAddress());
            }
            catch (Exception e) {
                publisherLog.warn("[PUBLISH ERROR] - Subscriber: " + s.getSubscriberAddress() + " failed.  Exception: " + e.getMessage());
            }
        }

        publisherLog.info("[PUBLISH BROADCAST COMPLETE]");
    }
}
