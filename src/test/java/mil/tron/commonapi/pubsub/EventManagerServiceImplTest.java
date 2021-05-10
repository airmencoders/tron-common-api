package mil.tron.commonapi.pubsub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import mil.tron.commonapi.dto.EventInfoDto;
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
import java.util.stream.Collectors;

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
    public void testGetMessagesSinceEventCount() {
        List<PubSubLedger> entries = Lists.newArrayList(
                PubSubLedger
                        .builder()
                        .id(UUID.randomUUID())
                        .eventType(EventType.PERSON_CHANGE)
                        .countForEventType(1L)
                        .data("")
                        .dateCreated(new Date(121, Calendar.MAY, 10, 12, 23, 0))
                        .build(),
                PubSubLedger
                        .builder()
                        .id(UUID.randomUUID())
                        .eventType(EventType.PERSON_DELETE)
                        .countForEventType(1L)
                        .data("")
                        .dateCreated(new Date(121, Calendar.MAY, 10, 12, 24, 0))
                        .build(),
                PubSubLedger
                        .builder()
                        .id(UUID.randomUUID())
                        .eventType(EventType.ORGANIZATION_CHANGE)
                        .countForEventType(1L)
                        .data("")
                        .dateCreated(new Date(121, Calendar.MAY, 10, 12, 25, 0))
                        .build(),
                PubSubLedger
                        .builder()
                        .id(UUID.randomUUID())
                        .eventType(EventType.ORGANIZATION_CHANGE)
                        .countForEventType(2L)
                        .data("")
                        .dateCreated(new Date(121, Calendar.MAY, 10, 12, 26, 0))
                        .build(),
                PubSubLedger
                        .builder()
                        .id(UUID.randomUUID())
                        .eventType(EventType.PERSON_CHANGE)
                        .countForEventType(2L)
                        .data("")
                        .dateCreated(new Date(121, Calendar.MAY, 10, 12, 27, 0))
                        .build(),
                PubSubLedger
                        .builder()
                        .id(UUID.randomUUID())
                        .eventType(EventType.ORGANIZATION_DELETE)
                        .countForEventType(1L)
                        .data("")
                        .dateCreated(new Date(121, Calendar.MAY, 10, 12, 28, 0))
                        .build(),
                PubSubLedger
                        .builder()
                        .id(UUID.randomUUID())
                        .eventType(EventType.PERSON_CHANGE)
                        .countForEventType(3L)
                        .data("")
                        .dateCreated(new Date(121, Calendar.MAY, 10, 12, 29, 0))
                        .build(),
                PubSubLedger
                        .builder()
                        .id(UUID.randomUUID())
                        .eventType(EventType.PERSON_CHANGE)
                        .countForEventType(4L)
                        .data("")
                        .dateCreated(new Date(121, Calendar.MAY, 10, 12, 30, 0))
                        .build(),
                PubSubLedger
                        .builder()
                        .id(UUID.randomUUID())
                        .eventType(EventType.PERSON_DELETE)
                        .countForEventType(2L)
                        .data("")
                        .dateCreated(new Date(121, Calendar.MAY, 10, 12, 31, 0))
                        .build(),
                PubSubLedger
                        .builder()
                        .id(UUID.randomUUID())
                        .eventType(EventType.PERSON_DELETE)
                        .countForEventType(3L)
                        .data("")
                        .dateCreated(new Date(121, Calendar.MAY, 10, 12, 32, 0))
                        .build(),
                PubSubLedger
                        .builder()
                        .id(UUID.randomUUID())
                        .eventType(EventType.PERSON_CHANGE)
                        .countForEventType(5L)
                        .data("")
                        .dateCreated(new Date(121, Calendar.MAY, 10, 12, 33, 0))
                        .build());

        Mockito.when(pubSubLedgerRepository.findByEventTypeEqualsAndCountForEventTypeEquals(EventType.PERSON_CHANGE, 4L))
                .thenReturn(Optional.of(entries.get(7)));

        Mockito.when(pubSubLedgerRepository.findByEventTypeEqualsAndCountForEventTypeEquals(EventType.PERSON_DELETE, 1L))
                .thenReturn(Optional.of(entries.get(1)));

        Mockito.when(pubSubLedgerRepository.findByDateCreatedGreaterThanEqual(entries.get(1).getDateCreated()))
                .thenReturn(entries.stream().skip(1L).collect(Collectors.toList()));

        assertEquals(5, Lists.newArrayList(eventManagerService
                .getMessagesSinceEventCountByType(Lists.newArrayList(
                        EventInfoDto
                                .builder()
                                .eventCount(3L)
                                .eventType(EventType.PERSON_CHANGE)
                                .build(),
                        EventInfoDto
                                .builder()
                                .eventCount(0L)
                                .eventType(EventType.PERSON_DELETE)
                                .build()))).size());

        assertEquals(0, Lists.newArrayList(eventManagerService.getMessagesSinceEventCountByType(new ArrayList<>())).size());
    }

    @Test
    public void testGetEventTypeCounts() {
        Mockito.when(pubSubLedgerRepository.countByEventType(Mockito.any(EventType.class)))
                .thenReturn(11L);

        assertNotNull(eventManagerService.getEventTypeCounts());
    }
}
