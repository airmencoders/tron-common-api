package mil.tron.commonapi.pubsub.listener;

import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import mil.tron.commonapi.pubsub.EventPublisher;
import mil.tron.commonapi.pubsub.listeners.OrganizationEntityListener;
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
public class OrganizationEntityListenerTest {

    @InjectMocks
    OrganizationEntityListener listener;

    @Mock
    EventPublisher publisher;

    private final HttpServletRequest testRequest = mock(HttpServletRequest.class);

    @Test
    void testOrganizationListener() {
        Mockito.when(testRequest.getHeader(Mockito.anyString())).thenReturn("");

        listener.afterAnyUpdate(new Organization());
        listener.afterAnyCreation(new Organization());
        listener.afterAnyRemoval(new Organization());
        Mockito
                .verify(publisher, times(3))
                .publishEvent(Mockito.any(EventType.class), Mockito.any(String.class), Mockito.any(String.class), Mockito.any(Object.class), Mockito.anyString());
    }
}

