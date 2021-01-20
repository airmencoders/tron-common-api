package mil.tron.commonapi.entity.pubsub;

import mil.tron.commonapi.entity.pubsub.events.EventTypes;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

public class SubscriberTests {

    @Test
    void testSubscriberToString() {
        Subscriber s = Subscriber
                .builder()
                .subscriberAddress("localhost")
                .subscribedEvent(EventTypes.AIRMAN_CHANGE)
                .build();

        assertNotNull(s.toString());
    }
}
