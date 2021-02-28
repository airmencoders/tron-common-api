package mil.tron.commonapi.pubsub.listener;

import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import mil.tron.commonapi.pubsub.EventPublisher;
import mil.tron.commonapi.pubsub.listeners.PersonEntityListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.http.HttpServletRequest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
public class PersonEntityListenerTest {

    @InjectMocks
    PersonEntityListener listener;

    @Mock
    EventPublisher publisher;

    private HttpServletRequest testRequest = mock(HttpServletRequest.class);

    @Test
    void testAirmanListener() {
        Mockito.when(testRequest.getHeader(Mockito.anyString())).thenReturn("");

        listener.afterAnyUpdate(new Person());
        listener.afterAnyCreation(new Person());
        listener.afterAnyRemoval(new Person());
        Mockito
            .verify(publisher, times(3))
            .publishEvent(Mockito.any(EventType.class), Mockito.any(String.class), Mockito.any(String.class), Mockito.any(Object.class), Mockito.anyString());
    }
}
