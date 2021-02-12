package mil.tron.commonapi.pubsub;

import com.google.common.collect.Lists;
import mil.tron.commonapi.entity.pubsub.Subscriber;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import mil.tron.commonapi.logging.CommonApiLogger;
import mil.tron.commonapi.security.AppClientPreAuthFilter;
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

import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    @Autowired
    private HttpServletRequest servletRequest;

    private static final String NAMESPACE_REGEX = "http://[^\\\\.]+\\.([^\\\\.]+)(?=\\.svc\\.cluster\\.local)";
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile(NAMESPACE_REGEX);

    public EventPublisher(SubscriberService subService) {
        this.subService = subService;
    }

    /**
     * Called by Entity Listeners for various types of JPA persistence changes.  Non-blocking operation.
     * @param type {@link EventType} type event to broadcast
     * @param message String message to send to subscribers
     * @param className the className of the entity that is the reason for this message
     * @param data data from the listener to embed into the push message
     */
    @Async
    public void publishEvent(EventType type, String message, String className, Object data) {

        // grab the requester from the x-forwarded-client-cert header
        //  so we can pass to pushevent - so that it knows not to push an event back to
        //  the requester
        String header = servletRequest.getHeader("x-forwarded-client-cert");
        String namespace = null;
        if (header != null) {
            String uri = AppClientPreAuthFilter.extractUriFromXfccHeader(header);
            namespace = AppClientPreAuthFilter.extractNamespaceFromUri(uri);
        }

        pushEvent(type, message, className, data, namespace);
    }

    /**
     * Called by the publishEvent async method.
     * @param type
     * @param message
     * @param className
     * @param data
     * @param requesterNamespace
     */
    private void pushEvent(EventType type, String message, String className, Object data, String requesterNamespace) {
        List<Subscriber> subscribers = Lists.newArrayList(subService.getSubscriptionsByEventType(type));

        Map<String, Object> messageDetails = new HashMap<>();
        messageDetails.put("event", type.toString());
        messageDetails.put("type", className);
        messageDetails.put("message", message);
        messageDetails.put("data", data);
        for (Subscriber s : subscribers) {
            String a = extractSubscriberNamespace(s.getSubscriberAddress());
            // only push to everyone but the requester (if the requester is a subscriber)
            if (!extractSubscriberNamespace(s.getSubscriberAddress()).equals(requesterNamespace)) {
                publisherLog.info("[PUBLISH BROADCAST] - Event: " + type.toString() + " Message: " + message + " to Subscriber: " + s.getSubscriberAddress());
                try {
                    publisherSender.postForLocation(s.getSubscriberAddress(), messageDetails);
                    publisherLog.info("[PUBLISH SUCCESS] - Subscriber: " + s.getSubscriberAddress());
                } catch (Exception e) {
                    publisherLog.warn("[PUBLISH ERROR] - Subscriber: " + s.getSubscriberAddress() + " failed.  Exception: " + e.getMessage());
                }
            }
        }

        publisherLog.info("[PUBLISH BROADCAST COMPLETE]");
    }

    public String extractSubscriberNamespace(String uri) {
        if (uri == null) return "";

        // namespace in a cluster local address should be 2nd element in the URI
        Matcher extractNs = NAMESPACE_PATTERN.matcher(uri);
        if (extractNs.find())
            return extractNs.group(1);
        else
            return "";
    }
}
