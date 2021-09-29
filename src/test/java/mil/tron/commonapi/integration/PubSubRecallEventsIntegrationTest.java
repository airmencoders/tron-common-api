package mil.tron.commonapi.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import mil.tron.commonapi.dto.EventInfoDto;
import mil.tron.commonapi.entity.pubsub.PubSubLedger;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import mil.tron.commonapi.repository.pubsub.PubSubLedgerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import javax.transaction.Transactional;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full stack test for the pub sub recall/replay feature.
 *
 */

@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
@ActiveProfiles(value = { "development", "test" })  // enable at least dev so we get tracing enabled for full integration
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
public class PubSubRecallEventsIntegrationTest {
    private static final String ENDPOINT_V2 = "/v2/subscriptions/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PubSubLedgerRepository pubSubLedgerRepository;

    private List<PubSubLedger> entries = Lists.newArrayList(
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

    @BeforeEach
    void setup() {

        pubSubLedgerRepository.deleteAllInBatch();
        pubSubLedgerRepository.flush();

        // populate our ledger
        pubSubLedgerRepository.saveAll(entries);

    }

    @AfterEach
    void cleanUp() {
        pubSubLedgerRepository.deleteAllInBatch();
        pubSubLedgerRepository.flush();
    }

    @Test
    @Transactional
    @Rollback
    void testReplaySinceTimeDate() throws Exception {
        mockMvc.perform(get(ENDPOINT_V2 + "events/replay?sinceDateTime=2021-05-09T12:00:00"))
        	.andExpect(jsonPath("$.data", hasSize(11)));
    }

    @Test
    @Transactional
    @Rollback
    void testReplayByEventCountAndType() throws Exception {

        // try it with since PERSON_CHANGE: 5L and PERSON_DELETE: 0L
        mockMvc.perform(post(ENDPOINT_V2 + "events/replay-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(
                        EventInfoDto
                                .builder()
                                .eventCount(3L)
                                .eventType(EventType.PERSON_CHANGE)
                                .build(),
                        EventInfoDto
                                .builder()
                                .eventCount(0L)
                                .eventType(EventType.PERSON_DELETE)
                                .build()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(5)));

        // try it with since PERSON_CHANGE: 4L and PERSON_DELETE: 2L
        mockMvc.perform(post(ENDPOINT_V2 + "events/replay-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(
                        EventInfoDto
                                .builder()
                                .eventCount(4L)
                                .eventType(EventType.PERSON_CHANGE)
                                .build(),
                        EventInfoDto
                                .builder()
                                .eventCount(2L)
                                .eventType(EventType.PERSON_DELETE)
                                .build()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));

        // try it with since PERSON_CHANGE: 1000L and PERSON_DELETE: 2L
        mockMvc.perform(post(ENDPOINT_V2 + "events/replay-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(
                        EventInfoDto
                                .builder()
                                .eventCount(1000L)
                                .eventType(EventType.PERSON_CHANGE)
                                .build(),
                        EventInfoDto
                                .builder()
                                .eventCount(2L)
                                .eventType(EventType.PERSON_DELETE)
                                .build()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        // try it with since PERSON_CHANGE: 1000L and PERSON_DELETE: 1000L
        mockMvc.perform(post(ENDPOINT_V2 + "events/replay-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(
                        EventInfoDto
                                .builder()
                                .eventCount(1000L)
                                .eventType(EventType.PERSON_CHANGE)
                                .build(),
                        EventInfoDto
                                .builder()
                                .eventCount(1000L)
                                .eventType(EventType.PERSON_DELETE)
                                .build()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        // try it with duplicate events, takes the latest
        mockMvc.perform(post(ENDPOINT_V2 + "events/replay-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(
                        EventInfoDto
                                .builder()
                                .eventCount(1000L)
                                .eventType(EventType.PERSON_CHANGE)
                                .build(),
                        EventInfoDto
                                .builder()
                                .eventCount(1000L)
                                .eventType(EventType.PERSON_DELETE)
                                .build(),
                        EventInfoDto
                                .builder()
                                .eventCount(4L)
                                .eventType(EventType.PERSON_CHANGE)
                                .build()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        // try it with a zero
        mockMvc.perform(post(ENDPOINT_V2 + "events/replay-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(
                        EventInfoDto
                                .builder()
                                .eventCount(0L)
                                .eventType(EventType.PERSON_CHANGE)
                                .build(),
                        EventInfoDto
                                .builder()
                                .eventCount(0L)
                                .eventType(EventType.PERSON_DELETE)
                                .build()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(8)));

        // try it with a negative number
        mockMvc.perform(post(ENDPOINT_V2 + "events/replay-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(
                        EventInfoDto
                                .builder()
                                .eventCount(-1L)
                                .eventType(EventType.PERSON_CHANGE)
                                .build(),
                        EventInfoDto
                                .builder()
                                .eventCount(0L)
                                .eventType(EventType.PERSON_DELETE)
                                .build()))))
                .andExpect(status().isBadRequest());

        // try it with a negative number
        mockMvc.perform(post(ENDPOINT_V2 + "events/replay-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(
                        EventInfoDto
                                .builder()
                                .eventCount(-1L)
                                .eventType(EventType.PERSON_CHANGE)
                                .build(),
                        EventInfoDto
                                .builder()
                                .eventCount(null)
                                .eventType(EventType.PERSON_DELETE)
                                .build()))))
                .andExpect(status().isBadRequest());
    }
}
