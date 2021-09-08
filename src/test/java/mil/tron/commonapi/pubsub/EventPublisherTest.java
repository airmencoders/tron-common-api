package mil.tron.commonapi.pubsub;

import com.google.common.collect.Lists;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.pubsub.Subscriber;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import mil.tron.commonapi.pubsub.messages.PersonChangedMessage;
import mil.tron.commonapi.pubsub.messages.PubSubMessage;
import mil.tron.commonapi.service.pubsub.SubscriberService;
import mil.tron.commonapi.service.pubsub.log.EventRequestService;
import mil.tron.commonapi.service.utility.IstioHeaderUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes=EventPublisher.class, properties = { "webhook-queue-max-size=5" })  // set limit to reasonable value
@TestPropertySource(locations = "classpath:application-test.properties")
public class EventPublisherTest {

    @Autowired
    @InjectMocks
    private EventPublisher publisher;

    @MockBean
    private SubscriberService subService;
    
    @MockBean
    private EventRequestService eventRequestService;

    @MockBean(name="eventSender")
    private RestTemplate publisherSender;

    private Subscriber subscriber, subscriber2;
    private PrintStream originalSystemOut = System.out;
    private final ByteArrayOutputStream outputStreamCaptor = new ByteArrayOutputStream();

    // mimic a real-formatted x-forwarded-client-cert header field that would be from a POST'r to the common api
    //  we'll see thru the code that this POSTr's XFCC header is from puckboard.
    //  The logic should see change is from puckboard and thus wont send push notification back to puckboard
    //  since they're the ones that originated the change
    private final String uri = "By=spiffe://cluster.local/ns/tron-common-api/sa/default;Hash=blah;Subject=\"\";URI=spiffe://cluster.local/ns/tron-puckboard/sa/default";

    @BeforeEach
    void setupMockSubscriber() {

        AppClientUser user1 = AppClientUser.builder()
                .name("Test")
                .clusterUrl("http://some.svc.cluster.local")
                .privileges(Set.of(Privilege.builder().id(1L).name("PERSON_READ").build()))
                .build();

        AppClientUser user2 = AppClientUser.builder()
                .name("Test")
                .clusterUrl("http://puckboard-api-service.tron-puckboard.svc.cluster.local")
                .privileges(Set.of(Privilege.builder().id(1L).name("PERSON_READ").build()))
                .build();

        subscriber = Subscriber.builder()
                .id(UUID.randomUUID())
                .appClientUser(user1)
                .subscribedEvent(EventType.PERSON_CHANGE)
                .subscriberAddress("/api/changed")
                .build();

        subscriber2 = Subscriber.builder()
                .id(UUID.randomUUID())
                .appClientUser(user2)
                .subscribedEvent(EventType.PERSON_CHANGE)
                // mimic real-formatted puckboard cluster URI as a subscriber
                .subscriberAddress("/puckboard-api/v1")
                .build();

        originalSystemOut = System.out;
        System.setOut(new PrintStream(outputStreamCaptor));
    }

    @AfterEach
    public void tearDown() throws Exception {
        System.setOut(originalSystemOut);
    }

    @Test
    void testAsyncPublish() {

        Mockito.when(subService.getSubscriptionsByEventType(Mockito.any(EventType.class)))
                .thenReturn(Lists.newArrayList(subscriber, subscriber2));
        Mockito.when(publisherSender.postForLocation(Mockito.anyString(), Mockito.any(PubSubMessage.class)))
                .thenReturn(URI.create(subscriber.getSubscriberAddress()));

        PersonChangedMessage message = new PersonChangedMessage();
        message.addPersonId(new Person().getId());

        publisher.publishEvent(message, uri);
        publisher.queueConsumer();  // manually tick the consumer

        // wait for publishEvent Async function, its a mocked function, so 1sec it more than enough but needed to avoid
        // a race condition on the logging output getting captured
        try { Thread.sleep(1000); } catch (InterruptedException e) {}

        assertTrue(outputStreamCaptor.toString().contains("[PUBLISH BROADCAST]"));
        assertFalse(outputStreamCaptor.toString().contains("[PUBLISH ERROR]"));

        // make sure we have only one broadcast push sent out of 2 subscribers (puckboard subscriber is omitted in this case)
        assertEquals(1, StringUtils.countOccurrencesOf(outputStreamCaptor.toString(), "[PUBLISH BROADCAST]"));
    }

    @Test
    void testAsyncPublishWithMalformedXFCCHeader() {
        // a malformed XFCC should just be ignored, and we just blast out the message to all subscribers

        Mockito.when(subService.getSubscriptionsByEventType(Mockito.any(EventType.class)))
                .thenReturn(Lists.newArrayList(subscriber, subscriber2));
        Mockito.when(publisherSender.postForLocation(Mockito.anyString(), Mockito.any(PubSubMessage.class)))
                .thenReturn(URI.create(subscriber.getSubscriberAddress()));

        PersonChangedMessage message = new PersonChangedMessage();
        message.addPersonId(new Person().getId());

        publisher.publishEvent(message, "");
        publisher.queueConsumer();  // manually tick the consumer

        // wait for publishEvent Async function, its a mocked function, so 1sec it more than enough but needed to avoid
        // a race condition on the logging output getting captured
        try { Thread.sleep(1000); } catch (InterruptedException e) {}

        assertTrue(outputStreamCaptor.toString().contains("[PUBLISH BROADCAST]"));
        assertFalse(outputStreamCaptor.toString().contains("[PUBLISH ERROR]"));

        assertEquals(2, StringUtils.countOccurrencesOf(outputStreamCaptor.toString(), "[PUBLISH BROADCAST]"));

    }

    @Test
    void testAsyncPublishFails() {

        Mockito.when(subService.getSubscriptionsByEventType(Mockito.any(EventType.class)))
                .thenReturn(Lists.newArrayList(subscriber));

        Mockito.when(
                publisherSender.postForLocation(Mockito.anyString(), Mockito.any()))
                .thenThrow(new RestClientException("Exception"));

        PersonChangedMessage message = new PersonChangedMessage();
        message.addPersonId(new Person().getId());

        publisher.publishEvent(message, uri);
        publisher.queueConsumer();  // manually tick the consumer

        // wait for publishEvent Async function, its a mocked function, so 1sec it more than enough but needed to avoid
        // a race condition on the logging output getting capture
        try { Thread.sleep(1000); } catch (InterruptedException e) {}

        assertTrue(outputStreamCaptor.toString().contains("[PUBLISH ERROR]"));
    }

    @Test
    void testQueueMaxedOut() {
        PersonChangedMessage message = new PersonChangedMessage();
        message.addPersonId(new Person().getId());

        // queue up 6 messages which exceeds our 5 limit setup for testing
        publisher.publishEvent(message, uri);
        publisher.publishEvent(message, uri);
        publisher.publishEvent(message, uri);
        publisher.publishEvent(message, uri);
        publisher.publishEvent(message, uri);
        publisher.publishEvent(message, uri);

        assertTrue(outputStreamCaptor.toString().contains("MAX QUEUE SIZE reached"));

        // dequeue everything
        publisher.queueConsumer();
        publisher.queueConsumer();
        publisher.queueConsumer();
        publisher.queueConsumer();
        publisher.queueConsumer();
    }

    @Test
    void testExtractNameSpaceFromURI() {
        assertEquals("tron-puckboard", IstioHeaderUtils.extractSubscriberNamespace("http://puckboard-api-service.tron-puckboard.svc.cluster.local/puckboard-api/v1"));
        assertEquals("", IstioHeaderUtils.extractSubscriberNamespace("http://cvc.cluster.local/puckboard-api/v1"));
        assertEquals("", IstioHeaderUtils.extractSubscriberNamespace("http://svc.cluster.local/puckboard-api/v1"));
        assertEquals("3000", IstioHeaderUtils.extractSubscriberNamespace("http://localhost:3000"));
        assertEquals("", IstioHeaderUtils.extractSubscriberNamespace(null));

        assertEquals("tron-puckboard", publisher.extractNamespace("By=spiffe://cluster.local/ns/tron-common-api/sa/default;Hash=blah;Subject=\"\";URI=spiffe://cluster.local/ns/tron-puckboard/sa/default"));
        assertEquals("3000", publisher.extractNamespace("By=spiffe://cluster.local/ns/tron-common-api/sa/default;Hash=blah;Subject=\"\";URI=spiffe://localhost:3000"));
        assertNull(publisher.extractNamespace(""));
    }
}
