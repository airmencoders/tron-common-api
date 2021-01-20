package mil.tron.commonapi.pubsub;

import com.google.common.collect.Lists;
import mil.tron.commonapi.entity.pubsub.Subscriber;
import mil.tron.commonapi.entity.pubsub.events.EventTypes;
import mil.tron.commonapi.service.pubsub.SubscriberService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class EventPublisherTest {

    @InjectMocks
    private EventPublisher publisher;

    @Mock
    private SubscriberService subService;

    @Mock
    private RestTemplate publisherSender;

    private Subscriber subscriber;
    private PrintStream originalSystemOut = System.out;
    private final ByteArrayOutputStream outputStreamCaptor = new ByteArrayOutputStream();

    @BeforeEach
    void setupMockSubscriber() {

        subscriber = Subscriber.builder()
                .id(UUID.randomUUID())
                .subscribedEvent(EventTypes.AIRMAN_CHANGE)
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
        Mockito.when(subService.getSubscriptionsByEventType(Mockito.any(EventTypes.class)))
                .thenReturn(Lists.newArrayList(subscriber));

        Mockito.when(
            publisherSender.getForEntity(subscriber.getSubscriberAddress(), String.class))
            .thenReturn(new ResponseEntity<>("message", HttpStatus.OK));

        publisher.publishEvent(EventTypes.AIRMAN_CHANGE, "message");

        assertTrue(outputStreamCaptor.toString().contains("[PUBLISH BROADCAST]"));
        assertFalse(outputStreamCaptor.toString().contains("[PUBLISH ERROR]"));
    }

    @Test
    void testAsyncPublishFails() {
        Mockito.when(subService.getSubscriptionsByEventType(Mockito.any(EventTypes.class)))
                .thenReturn(Lists.newArrayList(subscriber));

        Mockito.when(
                publisherSender.getForEntity(subscriber.getSubscriberAddress(), String.class))
                .thenThrow(new RestClientException("Exception"));


        publisher.publishEvent(EventTypes.AIRMAN_CHANGE, "message");
        assertTrue(outputStreamCaptor.toString().contains("[PUBLISH ERROR]"));
    }
}
