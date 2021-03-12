package mil.tron.commonapi.pubsub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import mil.tron.commonapi.entity.pubsub.PubSubLedger;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.pubsub.messages.PersonChangedMessage;
import mil.tron.commonapi.pubsub.messages.PubSubMessage;
import mil.tron.commonapi.repository.pubsub.PubSubLedgerRepository;
import mil.tron.commonapi.security.AppClientPreAuthFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
public class EventManagerServiceImplTest {

    @InjectMocks
    private EventManagerServiceImpl eventManagerService;

    @Mock
    private PubSubLedgerRepository pubSubLedgerRepository;

    @Mock
    private EventPublisher eventPublisher;

    private ObjectMapper objectMapper = mock(ObjectMapper.class);
    private HttpServletRequest testRequest = mock(HttpServletRequest.class);

    @Test
    public void testRecordEventAndPublish() throws Exception {
        Mockito.when(testRequest.getHeader(AppClientPreAuthFilter.XFCC_HEADER_NAME)).thenReturn("");
        Mockito.when(pubSubLedgerRepository.save(Mockito.any(PubSubLedger.class))).then(returnsFirstArg());
        Mockito.when(objectMapper.writeValueAsString(Mockito.any(PubSubMessage.class))).thenReturn("");
        PersonChangedMessage message = new PersonChangedMessage();
        message.setPersonIds(Set.of(UUID.randomUUID()));

        eventManagerService.recordEventAndPublish(message);

        Mockito
            .verify(eventPublisher, times(1))
            .publishEvent(Mockito.any(PubSubMessage.class), Mockito.anyString());

        assertThrows(BadRequestException.class, () ->  eventManagerService.recordEventAndPublish(null));

        Mockito.when(objectMapper.writeValueAsString(Mockito.any(PubSubMessage.class))).thenThrow(new JsonProcessingException("Error"){});

        assertThrows(BadRequestException.class, () ->  eventManagerService.recordEventAndPublish(message));
    }

    @Test
    public void testGetMessagesSinceDateTime() {
        PubSubLedger entry = PubSubLedger
                .builder()
                .id(UUID.randomUUID())
                .eventType(EventType.PERSON_CHANGE)
                .countForEventType(1L)
                .data("")
                .dateCreated(new Date())
                .build();

        Mockito.when(pubSubLedgerRepository.findByDateCreatedGreaterThan(Mockito.any(Date.class)))
                .thenReturn(Lists.newArrayList(entry));

        assertEquals(1, Lists.newArrayList(eventManagerService.getMessagesSinceDateTime(new Date())).size());
    }

    @Test
    public void testGetEventTypeCounts() {
        Mockito.when(pubSubLedgerRepository.countByEventType(Mockito.any(EventType.class)))
                .thenReturn(11L);

        assertNotNull(eventManagerService.getEventTypeCounts());
    }
}
