package mil.tron.commonapi.pubsub;

import com.google.common.collect.Lists;
import mil.tron.commonapi.entity.Airman;
import mil.tron.commonapi.entity.pubsub.Subscriber;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import mil.tron.commonapi.service.pubsub.SubscriberService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class EventPublisherTest {

    @Autowired
    @InjectMocks
    private EventPublisher publisher;

    @MockBean
    private SubscriberService subService;

    @MockBean(name="eventSender")
    private RestTemplate publisherSender;

    private Subscriber subscriber;
    private PrintStream originalSystemOut = System.out;
    private final ByteArrayOutputStream outputStreamCaptor = new ByteArrayOutputStream();

    @BeforeEach
    void setupMockSubscriber() {

        subscriber = Subscriber.builder()
                .id(UUID.randomUUID())
                .subscribedEvent(EventType.PERSON_CHANGE)
                .subscriberAddress("http://some.address/changed")
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
                .thenReturn(Lists.newArrayList(subscriber));

        Mockito.when(
            publisherSender.postForLocation(Mockito.anyString(), Mockito.anyMap()))
                .thenReturn(URI.create(subscriber.getSubscriberAddress()));

        publisher.publishEvent(EventType.PERSON_CHANGE, "message", "Airman", new Airman());

        // wait for publishEvent Async function, its a mocked function, so 1sec it more than enough but needed to avoid
        // a race condition on the logging output getting captured
        try { Thread.sleep(1000); } catch (InterruptedException e) {}

        assertTrue(outputStreamCaptor.toString().contains("[PUBLISH BROADCAST]"));
        assertFalse(outputStreamCaptor.toString().contains("[PUBLISH ERROR]"));
    }

    @Test
    void testAsyncPublishFails() {
        Mockito.when(subService.getSubscriptionsByEventType(Mockito.any(EventType.class)))
                .thenReturn(Lists.newArrayList(subscriber));

        Mockito.when(
                publisherSender.postForLocation(Mockito.anyString(), Mockito.anyMap()))
                .thenThrow(new RestClientException("Exception"));

        publisher.publishEvent(EventType.PERSON_CHANGE, "message", "Airman", new Airman());

        // wait for publishEvent Async function, its a mocked function, so 1sec it more than enough but needed to avoid
        // a race condition on the logging output getting capture
        try { Thread.sleep(1000); } catch (InterruptedException e) {}

        assertTrue(outputStreamCaptor.toString().contains("[PUBLISH ERROR]"));
    }
}
