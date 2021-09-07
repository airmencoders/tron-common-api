package mil.tron.commonapi.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.dto.pubsub.SubscriberDto;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.pubsub.Subscriber;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.PersonRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import mil.tron.commonapi.repository.pubsub.SubscriberRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.client.RestTemplate;

import java.text.SimpleDateFormat;
import java.util.*;

import static mil.tron.commonapi.security.Utility.hmac;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * Full stack test for the pub sub implementation.
 *
 */

@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties", properties = { "webhook-delay-ms=50" })
@ActiveProfiles(value = { "development", "test" })  // enable at least dev so we get tracing enabled for full integration
@AutoConfigureMockMvc
public class PubSubIntegrationTest {
    private static final String ENDPOINT_V2 = "/v2/subscriptions/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestTemplate eventSender;

    @Autowired
    private AppClientUserRespository appClientUserRespository;

    @Autowired
    private PersonRepository personRepo;

    @Autowired
    private SubscriberRepository subscriberRepository;

    @Autowired
    private PrivilegeRepository privilegeRepository;

    private MockRestServiceServer mockServer;

    private final static String SUB1_ADDRESS = "/changed";

    private final static String secret = "secret";

    AppClientUser user = AppClientUser.builder()
            .name("test")
            .clusterUrl("http://localhost:5005/")
            .build();

    private List<Privilege> privs = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {

        privs.clear();
        privs.addAll(privilegeRepository.findAll());
        user.setPrivileges(Set.of(privs.stream().filter(item -> item.getName().equals("PERSON_READ")).findFirst().get()));

        if (!appClientUserRespository.existsById(user.getId()))
            appClientUserRespository.save(user);

        mockServer = MockRestServiceServer.bindTo(eventSender).ignoreExpectOrder(true).build();
        SubscriberDto personChangeSub = SubscriberDto
                .builder()
                .appClientUser(user.getName())
                .subscribedEvent(EventType.PERSON_CHANGE)
                .subscriberAddress(SUB1_ADDRESS)
                .secret(secret)
                .build();

        SubscriberDto personDeleteSub = SubscriberDto
                .builder()
                .appClientUser(user.getName())
                .subscribedEvent(EventType.PERSON_DELETE)
                .subscriberAddress(SUB1_ADDRESS)
                .build();

        // setup some bogus subscriber
        //   that subscribes to PERSON messages
        mockMvc.perform(post(ENDPOINT_V2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(personChangeSub)))
                .andExpect(status().isOk());

        mockMvc.perform(post(ENDPOINT_V2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(personDeleteSub)))
                .andExpect(status().isOk());

    }

    @AfterEach
    void tearDown() {
        subscriberRepository.deleteAll();
        appClientUserRespository.delete(user);
        personRepo.deleteAllInBatch();
    }

    @Test
    void testSubscriberContacted() throws Exception {

        // test that the subscriber gets their message

        mockServer.expect(once(), requestTo(endsWith("/changed")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    assertFalse(request.getURI().toString().endsWith("//changed")); // make sure slash was stripped
                })
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

        mockMvc.perform(post("/v2/person")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(p)))
                .andExpect(status().isCreated());

        // must sleep a little since the broadcast is an async event, 1000ms is overkill but ok for test
        try { Thread.sleep(1000); } catch (InterruptedException e) {}

        mockServer.verify();
        mockServer.reset();

        // take away READ priv, the event publisher won't send to this address/subscriber for this event anymore
        mockServer.expect(never(), requestTo(endsWith("/changed")));
        user.setPrivileges(new HashSet<>());
        appClientUserRespository.save(user);

        PersonDto p2 = PersonDto
                .builder()
                .firstName("Homer")
                .lastName("Simpson")
                .email("hs@test.com")
                .rank("Maj")
                .branch(Branch.USAF)
                .build();

        mockMvc.perform(post("/v2/person")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(p2)))
                .andExpect(status().isCreated());

        try { Thread.sleep(1000); } catch (InterruptedException e) {}

        // verify nothing received
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

        mockMvc.perform(post("/v2/person")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(p)))
                .andExpect(status().isCreated());

        // must sleep a little since the broadcast is an async event, 1000ms is overkill but ok for test
        try { Thread.sleep(1000); } catch (InterruptedException e) {}

        mockServer.verify();

        // make subscriber go silent
        Date downTime = new Date();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));

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

        mockMvc.perform(post("/v2/person")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(p2)))
                .andExpect(status().isCreated());

        // go down for 3 secs
        try { Thread.sleep(3000); } catch (InterruptedException e) {}
        
        // come back up online, we should only get one change since our last communication time
        mockMvc.perform(get(ENDPOINT_V2 + "/events/replay?sinceDateTime={stamp}", df.format(downTime)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data", hasSize(1)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data[0].eventType", equalTo("PERSON_CHANGE")))
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

        mockMvc.perform(post("/v2/person/persons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(new PersonDto[] { p1, p2 })))
                .andExpect(status().isCreated());

        // must sleep a little since the broadcast is an async event, 1000ms is overkill but ok for test
        try { Thread.sleep(1000); } catch (InterruptedException e) {}

        mockServer.verify();
    }

    @Test
    void testHaveToHaveReadPrivToSubscribe() throws Exception {

        // an app client cannot subscribe to an event if they don't have
        //  READ priv on PERSON / ORG table are subscribing to
        // attempt to do so will result in 403

        // should already have PERSON_READ, now try to subscribe to ORGANIZATION_CHANGE -> should be 403
        SubscriberDto orgSub = SubscriberDto
                .builder()
                .appClientUser(user.getName())
                .subscribedEvent(EventType.ORGANIZATION_CHANGE)
                .subscriberAddress(SUB1_ADDRESS)
                .secret(secret)
                .build();

        // setup some bogus subscriber
        //   that subscribes to PERSON messages
        mockMvc.perform(post(ENDPOINT_V2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(orgSub)))
                .andExpect(status().isForbidden());

    }

    @Test
    void testCantMakeDuplicateSubscription() throws Exception {

        // should already have PERSON_CHANGE
        SubscriberDto orgSub = SubscriberDto
                .builder()
                .appClientUser(user.getName())
                .subscribedEvent(EventType.PERSON_CHANGE)
                .subscriberAddress(SUB1_ADDRESS)
                .secret(secret)
                .build();

        // should be a 409 conflict
        mockMvc.perform(post(ENDPOINT_V2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(orgSub)))
                .andExpect(status().isConflict());

    }

    @Test
    void testSecretAndUrlSynchronization() throws Exception {

        AppClientUser testApp = AppClientUser.builder()
                .id(UUID.randomUUID())
                .name("Test6")
                .privileges(Set.of(privs.stream()
                                .filter(item -> item.getName().equals("PERSON_READ"))
                                .findFirst()
                                .get()))
                .build();

        appClientUserRespository.save(testApp);

        SubscriberDto orgSub = SubscriberDto
                .builder()
                .appClientUser(testApp.getName())
                .subscribedEvent(EventType.PERSON_CHANGE)
                .subscriberAddress(SUB1_ADDRESS)
                .secret("")
                .build();

        // should be a bad request
        mockMvc.perform(post(ENDPOINT_V2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(orgSub)))
                .andExpect(status().isBadRequest());

        // should be a bad request
        orgSub.setSecret(null);
        mockMvc.perform(post(ENDPOINT_V2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(orgSub)))
                .andExpect(status().isBadRequest());

        orgSub.setSecret("secret");
        orgSub.setSubscriberAddress(null);
        mockMvc.perform(post(ENDPOINT_V2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(orgSub)))
                .andExpect(status().isBadRequest());

        // make it for real
        orgSub.setSubscriberAddress(SUB1_ADDRESS);
        mockMvc.perform(post(ENDPOINT_V2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(orgSub)))
                .andExpect(status().isOk());

        SubscriberDto orgSub2 = SubscriberDto
                .builder()
                .appClientUser(testApp.getName())
                .subscribedEvent(EventType.PERSON_DELETE)
                .subscriberAddress("/newurl")
                .secret("new_secret")
                .build();

        mockMvc.perform(post(ENDPOINT_V2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(orgSub2)))
                .andExpect(status().isOk());

        // check both subscriptions now have same secret and url
        subscriberRepository.findByAppClientUser(testApp).forEach(item -> {
            assertEquals("new_secret", item.getSecret());
            assertEquals("/newurl", item.getSubscriberAddress());
        });

        Subscriber sub = Lists.newArrayList(subscriberRepository.findByAppClientUser(testApp))
                .stream()
                .filter(item -> item.getSubscribedEvent().equals(EventType.PERSON_DELETE))
                .findFirst()
                .get();

        MvcResult result = mockMvc.perform(get(ENDPOINT_V2 + "/{id}", sub.getId())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        SubscriberDto dto = OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), SubscriberDto.class);
        dto.setSubscriberAddress("/correct_url");
        mockMvc.perform(post(ENDPOINT_V2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(dto)))
                .andExpect(status().isOk());

        // check both subscriptions still have same secret and url
        subscriberRepository.findByAppClientUser(testApp).forEach(item -> {
            assertEquals("new_secret", item.getSecret());
            assertEquals("/correct_url", item.getSubscriberAddress());
        });

    }
}
