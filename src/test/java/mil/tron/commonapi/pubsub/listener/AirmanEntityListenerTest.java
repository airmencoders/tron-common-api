package mil.tron.commonapi.pubsub.listener;

import mil.tron.commonapi.entity.Airman;
import mil.tron.commonapi.entity.pubsub.events.EventTypes;
import mil.tron.commonapi.pubsub.EventPublisher;
import mil.tron.commonapi.pubsub.listeners.AirmanEntityListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
public class AirmanEntityListenerTest {

    @InjectMocks
    AirmanEntityListener listener;

    @Mock
    EventPublisher publisher;

    @Test
    void testAirmanListener() {
        listener.afterAnyUpdate(new Airman());
        Mockito
            .verify(publisher, times(1))
            .publishEvent(Mockito.any(EventTypes.class), Mockito.any(String.class));
    }
}
