package mil.tron.commonapi.controller.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import mil.tron.commonapi.dto.EventInfoDto;
import mil.tron.commonapi.dto.pubsub.PubSubLedgerEntryDto;
import mil.tron.commonapi.dto.pubsub.SubscriberDto;
import mil.tron.commonapi.dto.pubsub.SubscriberDtoResponseWrapper;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import mil.tron.commonapi.pubsub.EventManagerService;
import mil.tron.commonapi.service.pubsub.SubscriberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
@AutoConfigureMockMvc
public class SubscriberControllerTests {
    private static final String ENDPOINT = "/v1/subscriptions";
    private static final String ENDPOINT_V2 = "/v2/subscriptions";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubscriberService subService;

    @MockBean
    private EventManagerService eventManagerService;

    private SubscriberDto subscriber;

    @BeforeEach
    public void insertSubscription() throws Exception {
        subscriber = SubscriberDto
                .builder()
                .id(UUID.randomUUID())
                .subscriberAddress("http://localhost:8080/changed")
                .subscribedEvent(EventType.PERSON_CHANGE)
                .build();
    }

    @Test
    @WithMockUser(username = "some@dude.com", authorities = { "DASHBOARD_ADMIN", "DASHBOARD_USER" })
    void testGetAllSubscriptions() throws Exception {
        Mockito.when(subService.getAllSubscriptions()).thenReturn(Lists.newArrayList(subscriber));
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(result ->
                        assertEquals(1, OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), SubscriberDto[].class).length));
        
        // V2
        mockMvc.perform(get(ENDPOINT_V2))
        .andExpect(status().isOk())
        .andExpect(result ->
                assertEquals(1, OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), SubscriberDtoResponseWrapper.class).getData().size()));
    }

    @Test
    void testGetSubscription() throws Exception {
        Mockito.when(subService.getSubscriberById(Mockito.any(UUID.class))).thenReturn(subscriber);
        mockMvc.perform(get(ENDPOINT + "/{id}", subscriber.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(result ->
                        assertEquals(subscriber.getId(), OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), SubscriberDto.class).getId()));
    }

    @Test
    void testCreateSubscription() throws Exception {
        Mockito.when(subService.upsertSubscription(subscriber)).thenReturn(subscriber);
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(subscriber)))
                    .andExpect(status().isOk())
                    .andExpect(result ->
                        assertEquals(subscriber.getId(), OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), SubscriberDto.class).getId()));
    }

    @Test
    void testDeleteSubscription() throws Exception {
        Mockito.doNothing().when(subService).cancelSubscription(subscriber.getId());
        mockMvc.perform(delete(ENDPOINT + "/{id}", subscriber.getId().toString()))
                .andExpect(status().isNoContent());
    }

    @Test
    void testGetLedgerEntriesSinceDateTime() throws Exception {
        Mockito.when(eventManagerService.getMessagesSinceDateTime(Mockito.any(Date.class)))
                .thenReturn(
                        Lists.newArrayList(PubSubLedgerEntryDto.builder().build()));

        mockMvc.perform(get(ENDPOINT + "/events/replay?sinceDateTime={dt}","2021-03-04T12:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        
        // V2
        mockMvc.perform(get(ENDPOINT_V2 + "/events/replay?sinceDateTime={dt}","2021-03-04T12:00:00"))
	        .andExpect(status().isOk())
	        .andExpect(jsonPath("$.data", hasSize(1)));

    }

    @Test
    void testGetLedgerEntriesSinceEventCountAndType() throws Exception {
        Mockito.when(eventManagerService.getMessagesSinceEventCountByType(Mockito.anyList()))
                .thenReturn(Set.of(PubSubLedgerEntryDto.builder().eventType(EventType.PERSON_CHANGE).countForEventType(1L).build()));

        mockMvc.perform(post(ENDPOINT + "/events/replay-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(
                        Lists.newArrayList(
                                EventInfoDto
                                    .builder()
                                    .eventCount(1L)
                                    .eventType(EventType.PERSON_CHANGE)
                                    .build(),
                                EventInfoDto
                                    .builder()
                                    .eventCount(2L)
                                    .eventType(EventType.PERSON_DELETE)
                                    .build()))))
                .andExpect(status().isOk());
        
        // V2
        mockMvc.perform(post(ENDPOINT_V2 + "/events/replay-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(
                        Lists.newArrayList(
                                EventInfoDto
                                    .builder()
                                    .eventCount(1L)
                                    .eventType(EventType.PERSON_CHANGE)
                                    .build(),
                                EventInfoDto
                                    .builder()
                                    .eventCount(2L)
                                    .eventType(EventType.PERSON_DELETE)
                                    .build()))))
                .andExpect(status().isOk());
    }

    @Test
    void testGetEventCounts() throws Exception {
        Mockito.when(eventManagerService.getEventTypeCounts()).thenReturn(new ArrayList<>());
        mockMvc.perform(get(ENDPOINT + "/events/latest")).andExpect(status().isOk());
        
        // V2
        mockMvc.perform(get(ENDPOINT_V2 + "/events/latest")).andExpect(status().isOk());
    }

}
