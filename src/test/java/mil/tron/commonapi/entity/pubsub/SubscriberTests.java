package mil.tron.commonapi.entity.pubsub;

import mil.tron.commonapi.entity.pubsub.events.EventType;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

public class SubscriberTests {

    @Test
    void testSubscriberToString() {
        Subscriber s = Subscriber
                .builder()
                .subscriberAddress("localhost")
                .subscribedEvent(EventType.PERSON_CHANGE)
                .build();

        assertNotNull(s.toString());
    }
}
