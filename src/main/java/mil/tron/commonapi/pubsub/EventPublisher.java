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

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service that fires off messages to subscribers for various events.  Entity listeners
 * call {@link EventPublisher#publishEvent(EventType, String, String, Object, String)} to launch an asynchronous
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

    private static final String NAMESPACE_REGEX =
            "(?:http://[^\\\\.]+\\.([^\\\\.]+)(?=\\.svc\\.cluster\\.local))" +  // match format for cluster-local URI
                    "|http://localhost:([0-9]+)";  // alternatively match localhost for dev/test

    private static final Pattern NAMESPACE_PATTERN = Pattern.compile(NAMESPACE_REGEX);

    public EventPublisher(SubscriberService subService) {
        this.subService = subService;
    }

    /**
     * Called by the publishEvent async method.
     * @param type
     * @param message
     * @param className
     * @param data
     * @param xfccHeader
     */
    @Async
    public void publishEvent(EventType type, String message, String className, Object data, String xfccHeader) {
        System.out.println("HEADER: " + xfccHeader);
        String requesterNamespace = null;
        if (xfccHeader != null) {
            String uri = AppClientPreAuthFilter.extractUriFromXfccHeader(xfccHeader);
            requesterNamespace = AppClientPreAuthFilter.extractNamespaceFromUri(uri);
        }

        List<Subscriber> subscribers = Lists.newArrayList(subService.getSubscriptionsByEventType(type));
        Map<String, Object> messageDetails = new HashMap<>();
        messageDetails.put("event", type.toString());
        messageDetails.put("type", className);
        messageDetails.put("message", message);
        messageDetails.put("data", data);
        for (Subscriber s : subscribers) {

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

    /**
     * Extracts the namespace from a cluster-local URI that is a subscriber's registered webhook URL
     * This is not to be confused with the URI in the XFCC header that Istio injects, that is something different.
     * This one has a format like http://app-name.ns.svc.cluster.local/ and represents a dns name local to the cluster
     * Alternatively for dev and test, it will be in format of http://localhost:(\d+), where the port number will
     * be the 'namespace'
     * @param uri Registered URL of the subscriber getting a broadcast
     * @return Namespace of the subscriber's webhook URL, or "" blank string if no namespace found
     */
    public String extractSubscriberNamespace(String uri) {
        if (uri == null) return "";

        // namespace in a cluster local address should be 2nd element in the URI
        Matcher extractNs = NAMESPACE_PATTERN.matcher(uri);
        int c = extractNs.groupCount();
        boolean found = extractNs.find();
        if (found && extractNs.group(1) != null) return extractNs.group(1);  // matched P1 cluster-local format
        else if (found && extractNs.group(2) != null) return extractNs.group(2);  // matched localhost format
        else return "";  // no matches found
    }
}
