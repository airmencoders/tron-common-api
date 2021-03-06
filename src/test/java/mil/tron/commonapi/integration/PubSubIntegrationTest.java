package mil.tron.commonapi.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.pubsub.Subscriber;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import org.aspectj.lang.annotation.After;
import org.hamcrest.core.AnyOf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.internal.matchers.Any;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestTemplate;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * Full stack test for the pub sub implementation.
 *
 */

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
public class PubSubIntegrationTest {
    private static final String ENDPOINT = "/v1/subscriptions/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestTemplate eventSender;

    private MockRestServiceServer mockServer;

    private final static String SUB1_ADDRESS = "http://localhost:5005/changed";

    private UUID sub1Id;
    private UUID sub2Id;

    @BeforeEach
    void setup() throws Exception {

        mockServer = MockRestServiceServer.bindTo(eventSender).ignoreExpectOrder(true).build();
        Subscriber personChangeSub = Subscriber
                .builder()
                .subscribedEvent(EventType.PERSON_CHANGE)
                .subscriberAddress(SUB1_ADDRESS)
                .build();

        Subscriber personDeleteSub = Subscriber
                .builder()
                .subscribedEvent(EventType.PERSON_DELETE)
                .subscriberAddress(SUB1_ADDRESS)
                .build();

        // setup some bogus subscriber
        //   that subscribes to PERSON messages
        MvcResult res1 = mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(personChangeSub)))
                .andExpect(status().isCreated())
                .andReturn();
        MvcResult res2 = mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(personDeleteSub)))
                .andExpect(status().isCreated())
                .andReturn();

        sub1Id = OBJECT_MAPPER.readValue(res1.getResponse().getContentAsString(), Subscriber.class).getId();
        sub2Id = OBJECT_MAPPER.readValue(res2.getResponse().getContentAsString(), Subscriber.class).getId();

    }

    @AfterEach
    void tearDown() throws Exception {

        mockMvc.perform(delete(ENDPOINT + "{id}", sub1Id)).andExpect(status().isNoContent());
        mockMvc.perform(delete(ENDPOINT + "{id}", sub2Id)).andExpect(status().isNoContent());
    }

    @Test
    void testSubscriberContacted() throws Exception {

        // test that the subscriber gets their message

        mockServer.expect(once(), requestTo(endsWith("/changed")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(result -> jsonPath("$.eventType", containsString("PERSON_CHANGE")))
                .andExpect(result -> jsonPath("$.eventType", not(containsString("ORGANIZATION_CHANGE"))))
                .andRespond(withSuccess());

        // make a new person
        PersonDto p = PersonDto
                .builder()
                .firstName("Joe")
                .lastName("Public")
                .email("jp@test.com")
                .rank("Capt")
                .branch(Branch.USAF)
                .build();

        mockMvc.perform(post("/v1/person")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(p)))
                .andExpect(status().isCreated());

        // must sleep a little since the broadcast is an async event, 1000ms is overkill but ok for test
        try { Thread.sleep(1000); } catch (InterruptedException e) {}

        mockServer.verify();
    }

    @Test
    void testSubscriberResync() throws Exception {

        // test that we can receive a message, then "go down", then come back up
        //   and ask what we missed since down time

        mockServer.expect(once(), requestTo(endsWith("/changed")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(result -> jsonPath("$.eventType", containsString("PERSON_CHANGE")))
                .andExpect(result -> jsonPath("$.eventType", not(containsString("ORGANIZATION_CHANGE"))))
                .andRespond(withSuccess());

        // make a new person
        PersonDto p = PersonDto
                .builder()
                .firstName("Joe")
                .lastName("Public")
                .email("jp2@test.com")
                .rank("Capt")
                .branch(Branch.USAF)
                .build();

        mockMvc.perform(post("/v1/person")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(p)))
                .andExpect(status().isCreated());

        // must sleep a little since the broadcast is an async event, 1000ms is overkill but ok for test
        try { Thread.sleep(1000); } catch (InterruptedException e) {}

        mockServer.verify();

        // make subscriber go silent
        Date downTime = new Date();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        mockServer.reset();

        // make a new person
        PersonDto p2 = PersonDto
                .builder()
                .firstName("Waylan")
                .lastName("Smithers")
                .email("ws@test.com")
                .rank("Maj")
                .branch(Branch.USAF)
                .build();

        mockMvc.perform(post("/v1/person")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(p2)))
                .andExpect(status().isCreated());

        // go down for 3 secs
        try { Thread.sleep(3000); } catch (InterruptedException e) {}

        // come back up online, we should only get one change since our last communication time
        mockMvc.perform(get(ENDPOINT + "/events/replay?sinceDateTime={stamp}", df.format(downTime)))
                .andExpect(result -> jsonPath("$", hasSize(1)))
                .andExpect(result -> jsonPath("$[0].eventType", equalTo("PERSON_CHANGE")))
                .andExpect(status().isOk());

    }

    @Test
    void testSubscriberGetsOneMessageForBulkAdd() throws Exception {

    }

}
