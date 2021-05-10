package mil.tron.commonapi.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.pubsub.Subscriber;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import mil.tron.commonapi.repository.PersonRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.client.RestTemplate;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import static mil.tron.commonapi.security.Utility.hmac;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * Full stack test for the pub sub implementation.
 *
 */

@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class PubSubIntegrationTest {
    private static final String ENDPOINT = "/v1/subscriptions/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestTemplate eventSender;

    @Autowired
    private PersonRepository personRepo;

    private MockRestServiceServer mockServer;

    private final static String SUB1_ADDRESS = "http://localhost:5005/changed";

    private final static String secret = "secret";

    private UUID sub1Id;
    private UUID sub2Id;

    @BeforeEach
    void setup() throws Exception {

        mockServer = MockRestServiceServer.bindTo(eventSender).ignoreExpectOrder(true).build();
        Subscriber personChangeSub = Subscriber
                .builder()
                .subscribedEvent(EventType.PERSON_CHANGE)
                .subscriberAddress(SUB1_ADDRESS)
                .secret(secret)
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
                .andExpect(status().isOk())
                .andReturn();
        MvcResult res2 = mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(personDeleteSub)))
                .andExpect(status().isOk())
                .andReturn();

        sub1Id = OBJECT_MAPPER.readValue(res1.getResponse().getContentAsString(), Subscriber.class).getId();
        sub2Id = OBJECT_MAPPER.readValue(res2.getResponse().getContentAsString(), Subscriber.class).getId();

    }

    @AfterEach
    void tearDown() throws Exception {

        mockMvc.perform(delete(ENDPOINT + "{id}", sub1Id)).andExpect(status().isNoContent());
        mockMvc.perform(delete(ENDPOINT + "{id}", sub2Id)).andExpect(status().isNoContent());
        personRepo.deleteAllInBatch();
    }

    @Test
    void testSubscriberContacted() throws Exception {

        // test that the subscriber gets their message

        mockServer.expect(once(), requestTo(endsWith("/changed")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(MockRestRequestMatchers.jsonPath("$.eventType", containsString("PERSON_CHANGE")))
                .andExpect(MockRestRequestMatchers.jsonPath("$.eventType", not(containsString("ORGANIZATION_CHANGE"))))
                .andExpect(request -> {
                    // verify the hmac is present and correct
                    MockClientHttpRequest mockRequest = (MockClientHttpRequest) request;
                    String body = mockRequest.getBodyAsString();
                    String signature = mockRequest.getHeaders().get("x-webhook-signature").stream().findFirst().orElseThrow(() -> new AssertionError("Missing signature header"));
                    assertEquals(hmac(secret, body), signature);
                })
                .andRespond(withSuccess());

        // make a new person
        PersonDto p = PersonDto
                .builder()
                .firstName("Montgomery")
                .lastName("Burns")
                .email("mb@test.com")
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
                .andExpect(MockRestRequestMatchers.jsonPath("$.eventType", containsString("PERSON_CHANGE")))
                .andExpect(MockRestRequestMatchers.jsonPath("$.eventType", not(containsString("ORGANIZATION_CHANGE"))))
                .andRespond(withSuccess());

        // make a new person
        PersonDto p = PersonDto
                .builder()
                .firstName("Homer")
                .lastName("Simpson")
                .email("hjs@test.com")
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
                .andExpect(MockMvcResultMatchers.jsonPath("$", hasSize(1)))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].eventType", equalTo("PERSON_CHANGE")))
                .andExpect(status().isOk());

    }

    @Test
    void testSubscriberGetsOneMessageForBulkPersonAdd() throws Exception {

        // test that we get only ONE pub sub message for a bulk person and
        //  bulk organization add (message body though contains numerous UUIDs of the new entities)

        mockServer.expect(once(), requestTo(endsWith("/changed")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(MockRestRequestMatchers.jsonPath("$.eventType", containsString("PERSON_CHANGE")))
                .andExpect(MockRestRequestMatchers.jsonPath("$.personIds", hasSize(2)))
                .andExpect(MockRestRequestMatchers.jsonPath("$.eventType", not(containsString("ORGANIZATION_CHANGE"))))
                .andRespond(withSuccess());

        // make a new person
        PersonDto p1 = PersonDto
                .builder()
                .firstName("Joe")
                .lastName("Public")
                .email("jp@test.com")
                .rank("Capt")
                .branch(Branch.USAF)
                .build();

        // make a another new person
        PersonDto p2 = PersonDto
                .builder()
                .firstName("Joe")
                .lastName("Public2")
                .email("jp2@test.com")
                .rank("Capt")
                .branch(Branch.USAF)
                .build();

        mockMvc.perform(post("/v1/person/persons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(new PersonDto[] { p1, p2 })))
                .andExpect(status().isCreated());

        // must sleep a little since the broadcast is an async event, 1000ms is overkill but ok for test
        try { Thread.sleep(1000); } catch (InterruptedException e) {}

        mockServer.verify();
    }

}
