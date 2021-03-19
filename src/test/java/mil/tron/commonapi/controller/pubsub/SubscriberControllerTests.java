package mil.tron.commonapi.controller.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import mil.tron.commonapi.entity.pubsub.PubSubLedger;
import mil.tron.commonapi.entity.pubsub.Subscriber;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
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
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubscriberService subService;

    @MockBean
    private EventManagerService eventManagerService;

    private Subscriber subscriber;

    @BeforeEach
    public void insertSubscription() throws Exception {
        subscriber = Subscriber
                .builder()
                .id(UUID.randomUUID())
                .subscriberAddress("http://localhost:8080/changed")
                .subscribedEvent(EventType.PERSON_CHANGE)
                .build();
    }

    @Test
    void testGetAllSubscriptions() throws Exception {
        Mockito.when(subService.getAllSubscriptions()).thenReturn(Lists.newArrayList(subscriber));
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(result ->
                        assertEquals(1, OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), Subscriber[].class).length));
    }

    @Test
    void testGetSubscription() throws Exception {
        Mockito.when(subService.getSubscriberById(Mockito.any(UUID.class))).thenReturn(subscriber);
        mockMvc.perform(get(ENDPOINT + "/{id}", subscriber.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(result ->
                        assertEquals(subscriber.getId(), OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), Subscriber.class).getId()));
    }

    @Test
    void testCreateSubscription() throws Exception {
        Mockito.when(subService.createSubscription(subscriber)).thenReturn(subscriber);
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(subscriber)))
                    .andExpect(status().isCreated())
                    .andExpect(result ->
                        assertEquals(subscriber.getId(), OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), Subscriber.class).getId()));
    }

    @Test
    void testUpdateSubscription() throws Exception {
        Mockito.when(subService.updateSubscription(subscriber.getId(), subscriber)).thenReturn(subscriber);
        mockMvc.perform(put(ENDPOINT + "/{id}", subscriber.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(subscriber)))
                .andExpect(status().isOk())
                .andExpect(result ->
                        assertEquals(subscriber.getId(), OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), Subscriber.class).getId()));
    }

    @Test
    void testDeleteSubscription() throws Exception {
        Mockito.doNothing().when(subService).cancelSubscription(subscriber.getId());
        mockMvc.perform(delete(ENDPOINT + "/{id}", subscriber.getId().toString()))
                .andExpect(status().isNoContent());
    }

    @Test
    void testGetLedgerEntries() throws Exception {
        Mockito.when(eventManagerService.getMessagesSinceDateTime(Mockito.any(Date.class)))
                .thenReturn(
                        Lists.newArrayList(PubSubLedger.builder().build()));

        mockMvc.perform(get(ENDPOINT + "/events/replay?sinceDateTime={dt}","2021-03-04T12:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

    }

    @Test
    void testGetEventCounts() throws Exception {
        Mockito.when(eventManagerService.getEventTypeCounts()).thenReturn(new HashMap<>());
        mockMvc.perform(get(ENDPOINT + "/events/latest")).andExpect(status().isOk());
    }

}
