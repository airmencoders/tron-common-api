package mil.tron.commonapi.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import mil.tron.commonapi.entity.pubsub.Subscriber;
import mil.tron.commonapi.logging.CommonApiLogger;
import mil.tron.commonapi.pubsub.messages.PubSubMessage;
import mil.tron.commonapi.security.AppClientPreAuthFilter;
import mil.tron.commonapi.service.pubsub.SubscriberService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    @Value("${webhook-send-timeout-secs:5}")
    private long webhookSendTimeoutSecs;

    /**
     * Publisher REST bean that will timeout after 5secs to a subscriber so that
     * a subscriber can't block/hang the publisher thread
     * @param builder
     * @return RestTemplate for use by the EventPublisher
     */
    @Bean("eventSender")
    public RestTemplate publisherSender(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(webhookSendTimeoutSecs))
                .setReadTimeout(Duration.ofSeconds(webhookSendTimeoutSecs))
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
            if (!extractSubscriberNamespace(s.getSubscriberAddress()).equals(requesterNamespace)) {
                publisherLog.info("[PUBLISH BROADCAST] - Event: " + message.getEventType().toString() + " to Subscriber: " + s.getSubscriberAddress());
                try {
                    String json = OBJECT_MAPPER.writeValueAsString(message);
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    if (s.getSecret() != null) {
                        headers.set(signatureHeader, hmac(s.getSecret(), json));
                    }
                    publisherSender.postForLocation(s.getSubscriberAddress(), new HttpEntity<>(json, headers));
                    publisherLog.info("[PUBLISH SUCCESS] - Subscriber: " + s.getSubscriberAddress());
                } catch (Exception e) {
                    publisherLog.warn("[PUBLISH ERROR] - Subscriber: " + s.getSubscriberAddress() + " failed.  Exception: " + e.getMessage());
                }
            }
        }

        publisherLog.info("[PUBLISH BROADCAST COMPLETE]");
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

    /**
     * Extracts the namespace from a cluster-local URI that is a subscriber's registered webhook URL
     * This is not to be confused with the URI in the XFCC header that Istio injects, that is something different.
     * This one has a format like http://app-name.ns.svc.cluster.local/ and represents a dns name local to the cluster
     *
     * Alternatively for dev and test, it will be in format of http://localhost:(\d+), where the port number will
     * be the so-called 'namespace'
     * @param uri Registered URL of the subscriber getting a broadcast
     * @return Namespace of the subscriber's webhook URL, or "" blank string if no namespace found
     */
    public String extractSubscriberNamespace(String uri) {
        if (uri == null) return "";

        // namespace in a cluster local address should be 2nd element in the URI
        Matcher extractNs = NAMESPACE_PATTERN.matcher(uri);
        boolean found = extractNs.find();
        if (found && extractNs.group(1) != null) return extractNs.group(1);  // matched P1 cluster-local format
        else if (found && extractNs.group(2) != null) return extractNs.group(2);  // matched localhost format
        else return "";  // no matches found
    }
}
