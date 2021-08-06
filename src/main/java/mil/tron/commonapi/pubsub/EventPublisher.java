package mil.tron.commonapi.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.pubsub.Subscriber;
import mil.tron.commonapi.logging.CommonApiLogger;
import mil.tron.commonapi.pubsub.messages.PubSubMessage;
import mil.tron.commonapi.security.AppClientPreAuthFilter;
import mil.tron.commonapi.service.pubsub.SubscriberService;
import mil.tron.commonapi.service.pubsub.SubscriberServiceImpl;
import mil.tron.commonapi.service.utility.IstioHeaderUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static mil.tron.commonapi.security.Utility.hmac;

/**
 * A Service that fires off messages to subscribers for various events.
 *
 * The EventManagerService calls {@link EventPublisher#publishEvent(PubSubMessage, String)} to launch an asynchronous
 * broadcast to subscribers for the provided event type.
 */
@Service
public class EventPublisher {
    private final Log publisherLog = LogFactory.getLog(CommonApiLogger.class);

    @Value("${signature-header}")
    private String signatureHeader;

    @Value("${webhook-queue-max-size:1000000}")
    private long webhookQueueSize;

    private SubscriberService subService;

    @Autowired
    @Qualifier("eventSender")
    private RestTemplate publisherSender;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private ConcurrentLinkedQueue<EnqueuedEvent> queuedEvents = new ConcurrentLinkedQueue<>();

    /**
     * Inner class to holds a pub sub message and the x-forwarded-client-cert header
     * of the requester/initiator
     */
    @AllArgsConstructor
    @Builder
    public static class EnqueuedEvent {
        @Getter
        private PubSubMessage message;

        @Getter
        private String xfccHeader;
    }

    public EventPublisher(SubscriberService subService) {
        this.subService = subService;
    }

    /**
     * Called by the publishEvent async method.  This enqueues the message into the
     * publisher queue.
     * @param message the PubSub message to send
     * @param xfccHeader the xfccHeader of where the request came from (the initiating app/entity)
     */
    @Async
    public void publishEvent(PubSubMessage message, String xfccHeader) {

        // a concurrent linked queue will grow until out of system memory
        //  we cap it ourselves to 'webhookQueueSize'
        if (queuedEvents.size() >= webhookQueueSize) {
            publisherLog.error("MAX QUEUE SIZE reached - dropping pubsub message");
            return;
        }

        // queue this message to be picked up by the queue consumer
        //  enqueue is guaranteed not to block
        queuedEvents.offer(EnqueuedEvent
                .builder()
                .message(message)
                .xfccHeader(xfccHeader)
                .build());

    }

    // if subscriber's app-client is null or somehow doesn't have an url assigned - then return false
    private boolean subscriberUrlValid(Subscriber s) {
        return (s.getAppClientUser() != null
                && s.getAppClientUser().getClusterUrl() != null
                && !s.getAppClientUser().getClusterUrl().isBlank());
    }

    // construct full URL from the app client's URL + the path the app developer listed in the subscription
    // gid rid of leading slash in subscriber's path if present (since app clients URL is required to have /)
    private String buildSubscriberFullUrl(Subscriber s) {
        if (s.getSubscriberAddress().startsWith("/")) {
            return s.getAppClientUser().getClusterUrl() + s.getSubscriberAddress().substring(1);
        }
        else {
            return s.getAppClientUser().getClusterUrl() + s.getSubscriberAddress();
        }
    }

    /**
     * The event queue consumer that operates on a fixed period, and pops a queued
     * event from the event queue and sends it out to the subscribers.
     */
    @Scheduled(fixedDelayString = "${webhook-delay-ms}")
    public void queueConsumer() {

        if (queuedEvents.isEmpty()) return;

        EnqueuedEvent event = queuedEvents.poll();
        if (event == null) return;

        PubSubMessage message = event.getMessage();
        String xfccHeader = event.getXfccHeader();

        // get the cluster-namespace of the requester from headers who is driving this change, so we don't
        //  send them the change - which wouldn't make any sense
        String requesterNamespace = extractNamespace(xfccHeader);

        // get the list of subscribers for this type of eventType
        List<Subscriber> subscribers = Lists.newArrayList(subService.getSubscriptionsByEventType(message.getEventType()));

        publisherLog.info("[QUEUE SIZE] - " + queuedEvents.size());

        // start publish loop - only push to everyone but the requester (...if the requester is a subscriber)
        for (Subscriber s : subscribers) {

            if (!subscriberUrlValid(s)) {
                publisherLog.info(String.format("[PUBLISH WARNING] - Subscription ID %s does not have an app-client or cluster url, skipping", s.getId()));
                continue;
            }

            // make sure the target subscriber has at least READ access to the type of entity the event describes the change for
            //  otherwise ignore them - since there's no use to send the change
            if (!checkSubscriberHasReadPrivsForEvent(s)) {
                publisherLog.info(String.format("[PUBLISH WARNING] - Subscription ID %s does not have READ access to the entity type of the event", s.getId()));
                continue;
            }

            String subscriberUrl = buildSubscriberFullUrl(s);

            if (!IstioHeaderUtils.extractSubscriberNamespace(subscriberUrl).equals(requesterNamespace)) {
                publisherLog.info("[PUBLISH BROADCAST] - Event: " + message.getEventType().toString() + " to Subscriber: " + subscriberUrl);
                try {
                    String json = OBJECT_MAPPER.writeValueAsString(message);
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    if (s.getSecret() != null) {
                        headers.set(signatureHeader, hmac(s.getSecret(), json));
                    }
                    publisherSender.postForLocation(subscriberUrl, new HttpEntity<>(json, headers));
                    publisherLog.info("[PUBLISH SUCCESS] - Subscriber: " + subscriberUrl);
                } catch (Exception e) {
                    publisherLog.warn("[PUBLISH ERROR] - Subscriber: " + subscriberUrl + " failed.  Exception: " + e.getMessage());
                }
            }
        }

        publisherLog.info("[PUBLISH BROADCAST COMPLETE]");
    }

    /**
     * Helper to check if a given subscriber has read access for the type of entity that they're about
     * to send a message event to... if they don't have that entity's type of READ permission then its
     * no use sending them the event
     * @param subscriber
     * @return
     */
    private boolean checkSubscriberHasReadPrivsForEvent(Subscriber subscriber) {
        String type = SubscriberServiceImpl.getTargetEntityType(subscriber.getSubscribedEvent());
        return subscriber
                .getAppClientUser()
                .getPrivileges()
                .stream()
                .map(Privilege::getName)
                .collect(Collectors.toList())
                .contains(type + "_READ");

    }

    /**
     * Helper to extract out the namespace, it prefers the regex used
     * in the auth filter (for a real P1 xfcc header), but if that fails
     * then it tries to match a localhost address and call the 'port' the
     * returned 'namespace'
     * @param xfccHeader
     * @return namespace (or port number if xfcc is a spoofed localhost one)
     */
    public String extractNamespace(String xfccHeader) {
        String requesterNamespace = null;
        if (xfccHeader != null) {
            String uri = AppClientPreAuthFilter.extractUriFromXfccHeader(xfccHeader);
            requesterNamespace = AppClientPreAuthFilter.extractNamespaceFromUri(uri);

            if (requesterNamespace == null) {
                // check out the xfcc for localhost for testing...
                //
                String localPortRegex = "localhost:([0-9]+)";
                Pattern pattern = Pattern.compile(localPortRegex);
                Matcher extractPort = pattern.matcher(xfccHeader);
                if (extractPort.find()) {
                  return extractPort.group(1);
                }
            }
        }
        return requesterNamespace;
    }
}
