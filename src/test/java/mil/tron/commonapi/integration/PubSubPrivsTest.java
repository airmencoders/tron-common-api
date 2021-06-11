package mil.tron.commonapi.integration;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import mil.tron.commonapi.dto.EventInfoDto;
import mil.tron.commonapi.dto.pubsub.SubscriberDto;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import mil.tron.commonapi.repository.pubsub.SubscriberRepository;
import static org.hamcrest.Matchers.hasSize;

import org.assertj.core.util.Lists;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.internal.hamcrest.HamcrestArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties", properties = { "webhook-delay-ms=50", "security.enabled=true" })
@ActiveProfiles(value = { "development", "test" })  // enable at least dev so we get tracing enabled for full integration
@AutoConfigureMockMvc
public class PubSubPrivsTest {

    private static final String XFCC_HEADER_NAME = "x-forwarded-client-cert";
    private static final String AUTH_HEADER_NAME = "authorization";


    private static final String ENDPOINT_V2 = "/v2/subscriptions/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private DashboardUser adminUser;
    private UUID adminId = UUID.randomUUID();

    private DashboardUser devUser;
    private UUID devUserId = UUID.randomUUID();

    private DashboardUser devUser2;
    private UUID devUser2Id = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubscriberRepository subscriberRepository;

    @Autowired
    private AppClientUserRespository appClientUserRespository;

    @Autowired
    private DashboardUserRepository dashboardUserRepository;

    @Autowired
    private PrivilegeRepository privilegeRepository;

    @BeforeEach
    void setup() {

        // add the dashboard admin
        // create the admin
        adminUser = DashboardUser.builder()
                .id(adminId)
                .email("admin@tester.com")
                .privileges(Set.of(
                        privilegeRepository.findByName("DASHBOARD_ADMIN").orElseThrow(() -> new RecordNotFoundException("No DASH_BOARD ADMIN")),
                        privilegeRepository.findByName("DASHBOARD_USER").orElseThrow(() -> new RecordNotFoundException("No DASH_BOARD USER"))))
                .build();

        dashboardUserRepository.save(adminUser);

        // add the dashboard dev user for GA
        devUser = DashboardUser.builder()
                .id(devUserId)
                .email("dev@tester.com")
                .privileges(Set.of(
                        privilegeRepository.findByName("DASHBOARD_USER").orElseThrow(() -> new RecordNotFoundException("No DASH_BOARD USER")),
                        privilegeRepository.findByName("APP_CLIENT_DEVELOPER").orElseThrow(() -> new RecordNotFoundException("No App Client Developer"))))
                .build();

        dashboardUserRepository.save(devUser);

        // add them to GA
        AppClientUser guardianAngel = appClientUserRespository.findByNameIgnoreCase("guardianangel").get();
        guardianAngel.setAppClientDevelopers(Set.of(devUser));

        // add the dashboard dev user for general use
        devUser2 = DashboardUser.builder()
                .id(devUser2Id)
                .email("dev2@tester.com")
                .privileges(Set.of(
                        privilegeRepository.findByName("DASHBOARD_USER").orElseThrow(() -> new RecordNotFoundException("No DASH_BOARD USER")),
                        privilegeRepository.findByName("APP_CLIENT_DEVELOPER").orElseThrow(() -> new RecordNotFoundException("No App Client Developer"))))
                .build();

        dashboardUserRepository.save(devUser2);

    }

    @AfterEach
    void reset() {

        // delete all subscriptions
        subscriberRepository.deleteAll();

        // remove "NewApp"
        appClientUserRespository
                .findByNameIgnoreCase("NewApp")
                .ifPresent(client -> appClientUserRespository.delete(client));

        // clean up users
        dashboardUserRepository.deleteById(adminId);
        dashboardUserRepository.deleteById(devUserId);
        dashboardUserRepository.deleteById(devUser2Id);
    }

    /**
     * Test that only registered app clients, can use pubsub rest interface
     * @throws Exception
     */
    @Test
    void testPubSubAsAnAppClient() throws Exception {

        // this subscript for guardianangel should be OK
        mockMvc.perform(post(ENDPOINT_V2)
                .header(XFCC_HEADER_NAME, generateXfccHeader("guardianangel"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(SubscriberDto.builder()
                        .id(UUID.randomUUID())
                        .appClientUser("guardianangel")
                        .subscribedEvent(EventType.PERSON_CHANGE)
                        .subscriberAddress("/")
                        .secret("blah")
                        .build())))
                .andExpect(status().isOk());

        // this one should be denied - not a registered client app
        mockMvc.perform(post(ENDPOINT_V2)
                .header(XFCC_HEADER_NAME, generateXfccHeader("NewApp"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(SubscriberDto.builder()
                    .id(UUID.randomUUID())
                    .appClientUser("NewApp")
                    .subscribedEvent(EventType.PERSON_CHANGE)
                    .subscriberAddress("/")
                    .secret("blah")
                    .build())))
                .andExpect(status().isForbidden());

        // make the "NewApp" app client
        appClientUserRespository.saveAndFlush(AppClientUser.builder()
            .name("NewApp")
            .privileges(new HashSet<>())
            .appClientDevelopers(new HashSet<>())
            .build());

        // should work now
        MvcResult result = mockMvc.perform(post(ENDPOINT_V2)
                .header(XFCC_HEADER_NAME, generateXfccHeader("NewApp"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(SubscriberDto.builder()
                        .id(UUID.randomUUID())
                        .appClientUser("NewApp")
                        .subscribedEvent(EventType.PERSON_CHANGE)
                        .subscriberAddress("/")
                        .secret("blah")
                        .build())))
                .andExpect(status().isOk())
                .andReturn();

        UUID id = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));

        // should be able to edit
        mockMvc.perform(post(ENDPOINT_V2)
                .header(XFCC_HEADER_NAME, generateXfccHeader("NewApp"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(SubscriberDto.builder()
                        .id(id)
                        .appClientUser("NewApp")
                        .subscribedEvent(EventType.PERSON_DELETE)
                        .subscriberAddress("/")
                        .secret("blah")
                        .build())))
                .andExpect(status().isOk());

        // should be able to delete its own subscription
        mockMvc.perform(delete(ENDPOINT_V2 + "{id}", id)
                .header(XFCC_HEADER_NAME, generateXfccHeader("NewApp"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(SubscriberDto.builder()
                        .id(id)
                        .appClientUser("NewApp")
                        .subscribedEvent(EventType.PERSON_DELETE)
                        .subscriberAddress("/")
                        .secret("blah")
                        .build())))
                .andExpect(status().isNoContent());
    }

    /**
     * Test that only app devs of registered app clients, can use pubsub rest interface
     * @throws Exception
     */
    @Test
    void testPubSubAsAnAppDeveloper() throws Exception {

        // subscription management by guardian angel's developer should work
        MvcResult result = mockMvc.perform(post(ENDPOINT_V2)
                .header(AUTH_HEADER_NAME, createToken(devUser.getEmail()))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(SubscriberDto.builder()
                        .id(UUID.randomUUID())
                        .appClientUser("guardianangel")
                        .subscribedEvent(EventType.PERSON_CHANGE)
                        .subscriberAddress("/")
                        .secret("blah")
                        .build())))
                .andExpect(status().isOk())
                .andReturn();

        UUID id = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));

        // GA's subscription management by other app developer should NOT work
        mockMvc.perform(post(ENDPOINT_V2)
                .header(AUTH_HEADER_NAME, createToken(devUser2.getEmail()))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(SubscriberDto.builder()
                        .id(id)
                        .subscribedEvent(EventType.PERSON_CHANGE)
                        .appClientUser("guardianangel")
                        .subscriberAddress("/")
                        .secret("blah")
                        .build())))
                .andExpect(status().isForbidden());


        // other app developer shouldnt be able to delete GA subscription either
        mockMvc.perform(delete(ENDPOINT_V2 + "{id}", id)
                .header(AUTH_HEADER_NAME, createToken(devUser2.getEmail()))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testCanGetAppropriateSubscriptionList() throws Exception {

        // make the "NewApp" app client
        // assign the devUser2 to be an developer for it
        appClientUserRespository.saveAndFlush(AppClientUser
                .builder()
                .name("NewApp")
                .privileges(new HashSet<>())
                .appClientDevelopers(Set.of(devUser2))
                .build());

        // sign the NewApp up for pub sub
        mockMvc.perform(post(ENDPOINT_V2)
                .header(AUTH_HEADER_NAME, createToken(devUser2.getEmail()))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(SubscriberDto.builder()
                        .id(UUID.randomUUID())
                        .appClientUser("NewApp")
                        .subscribedEvent(EventType.PERSON_CHANGE)
                        .subscriberAddress("/")
                        .secret("blah")
                        .build())))
                .andExpect(status().isOk());

        // sign up GA for pubsub
        mockMvc.perform(post(ENDPOINT_V2)
                .header(AUTH_HEADER_NAME, createToken(adminUser.getEmail()))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(SubscriberDto.builder()
                        .id(UUID.randomUUID())
                        .appClientUser("guardianangel")
                        .subscribedEvent(EventType.PERSON_CHANGE)
                        .subscriberAddress("/")
                        .secret("blah")
                        .build())))
                .andExpect(status().isOk());

        // now get a list of subscriptions as the devUser2 guy
        //  should just be the "NewApp"
        mockMvc.perform(get(ENDPOINT_V2)
                .header(AUTH_HEADER_NAME, createToken(devUser2.getEmail()))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        // now get a list of subscriptions as the "NewApp" application itself
        //  should just be the one
        mockMvc.perform(get(ENDPOINT_V2)
                .header(XFCC_HEADER_NAME, generateXfccHeader("NewApp")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        // request as DASHBOARD_ADMIN - should be 2
        mockMvc.perform(get(ENDPOINT_V2)
                .header(AUTH_HEADER_NAME, createToken(adminUser.getEmail()))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));

    }

    @Test
    void testCanGetSingleAppSubscriptionEntity() throws Exception {

        // make the "NewApp" app client
        // assign the devUser2 to be an developer for it
        appClientUserRespository.saveAndFlush(AppClientUser
                .builder()
                .name("NewApp")
                .privileges(new HashSet<>())
                .appClientDevelopers(Set.of(devUser2))
                .build());

        // sign the NewApp up for pub sub
        MvcResult result = mockMvc.perform(post(ENDPOINT_V2)
                .header(AUTH_HEADER_NAME, createToken(devUser2.getEmail()))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(SubscriberDto.builder()
                        .id(UUID.randomUUID())
                        .appClientUser("NewApp")
                        .subscribedEvent(EventType.PERSON_CHANGE)
                        .subscriberAddress("/")
                        .secret("blah")
                        .build())))
                .andExpect(status().isOk())
                .andReturn();

        UUID id = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));

        // now get the subscription as the "NewApp" application itself
        mockMvc.perform(get(ENDPOINT_V2 + "{id}", id)
                .header(XFCC_HEADER_NAME, generateXfccHeader("NewApp")))
                .andExpect(status().isOk());

        // now get the subscription as a "NewApp" developer
        mockMvc.perform(get(ENDPOINT_V2 + "{id}", id)
                .header(AUTH_HEADER_NAME, createToken(devUser2.getEmail()))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // now get the subscription as some random person
        mockMvc.perform(get(ENDPOINT_V2 + "{id}", id)
                .header(AUTH_HEADER_NAME, createToken("dude@tester.com"))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO()))
                .andExpect(status().isForbidden());

        // now get the subscription as admin
        mockMvc.perform(get(ENDPOINT_V2 + "{id}", id)
                .header(AUTH_HEADER_NAME, createToken(adminUser.getEmail()))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

    }

    @Test
    void testReplayPrivs() throws Exception {

        // test only that APP_CLIENT_DEV / APP_CLIENT / or DASHBOARD_ADMIN can hit the replay endpoint
        mockMvc.perform(get(ENDPOINT_V2 + "events/replay?sinceDateTime=2020-05-21T12:00:00")
                .header(AUTH_HEADER_NAME, createToken(adminUser.getEmail()))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        mockMvc.perform(get(ENDPOINT_V2 + "events/replay?sinceDateTime=2020-05-21T12:00:00")
                .header(AUTH_HEADER_NAME, createToken(devUser.getEmail()))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        mockMvc.perform(get(ENDPOINT_V2 + "events/replay?sinceDateTime=2020-05-21T12:00:00")
                .header(AUTH_HEADER_NAME, createToken(devUser2.getEmail()))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        mockMvc.perform(get(ENDPOINT_V2 + "events/replay?sinceDateTime=2020-05-21T12:00:00")
                .header(XFCC_HEADER_NAME, generateXfccHeader("guardianangel")))
                .andExpect(status().isOk());

        mockMvc.perform(get(ENDPOINT_V2 + "events/replay?sinceDateTime=2020-05-21T12:00:00")
                .header(AUTH_HEADER_NAME, createToken("dude@tester.com"))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testEventCountPrivs() throws Exception {

        // test only that APP_CLIENT_DEV / APP_CLIENT / or DASHBOARD_ADMIN can hit the event Count endpoint
        mockMvc.perform(get(ENDPOINT_V2 + "events/latest")
                .header(AUTH_HEADER_NAME, createToken(adminUser.getEmail()))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        mockMvc.perform(get(ENDPOINT_V2 + "events/latest")
                .header(AUTH_HEADER_NAME, createToken(devUser.getEmail()))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        mockMvc.perform(get(ENDPOINT_V2 + "events/latest")
                .header(AUTH_HEADER_NAME, createToken(devUser2.getEmail()))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        mockMvc.perform(get(ENDPOINT_V2 + "events/latest")
                .header(XFCC_HEADER_NAME, generateXfccHeader("guardianangel")))
                .andExpect(status().isOk());

        mockMvc.perform(get(ENDPOINT_V2 + "events/latest")
                .header(AUTH_HEADER_NAME, createToken("dude@tester.com"))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testReplayEventsPrivs() throws Exception {

        // test only that APP_CLIENT_DEV / APP_CLIENT / or DASHBOARD_ADMIN can hit the event Count endpoint
        mockMvc.perform(post(ENDPOINT_V2 + "events/replay-events")
                .header(AUTH_HEADER_NAME, createToken(adminUser.getEmail()))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(EventInfoDto
                        .builder()
                        .eventCount(1L)
                        .eventType(EventType.PERSON_CHANGE)
                        .build()))))
                .andExpect(status().isOk());

        mockMvc.perform(post(ENDPOINT_V2 + "events/replay-events")
                .header(AUTH_HEADER_NAME, createToken(devUser.getEmail()))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(EventInfoDto
                        .builder()
                        .eventCount(1L)
                        .eventType(EventType.PERSON_CHANGE)
                        .build()))))
                .andExpect(status().isOk());

        mockMvc.perform(post(ENDPOINT_V2 + "events/replay-events")
                .header(AUTH_HEADER_NAME, createToken(devUser2.getEmail()))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(EventInfoDto
                        .builder()
                        .eventCount(1L)
                        .eventType(EventType.PERSON_CHANGE)
                        .build()))))
                .andExpect(status().isOk());

        mockMvc.perform(post(ENDPOINT_V2 + "events/replay-events")
                .header(XFCC_HEADER_NAME, generateXfccHeader("guardianangel"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(EventInfoDto
                        .builder()
                        .eventCount(1L)
                        .eventType(EventType.PERSON_CHANGE)
                        .build()))))
                .andExpect(status().isOk());

        mockMvc.perform(post(ENDPOINT_V2 + "events/replay-events")
                .header(AUTH_HEADER_NAME, createToken("dude@tester.com"))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(EventInfoDto
                        .builder()
                        .eventCount(1L)
                        .eventType(EventType.PERSON_CHANGE)
                        .build()))))
                .andExpect(status().isForbidden());
    }

    String generateXfccHeader(String namespace) {
        String XFCC_BY = "By=spiffe://cluster/ns/" + namespace + "/sa/default";
        String XFCC_H = "FAKE_H=12345";
        String XFCC_SUBJECT = "Subject=\\\"\\\";";
        return new StringBuilder()
                .append(XFCC_BY)
                .append(XFCC_H)
                .append(XFCC_SUBJECT)
                .append("URI=spiffe://cluster.local/ns/" + namespace + "/sa/default")
                .toString();
    }

    /**
     * Helper to generate a XFCC header from the istio gateway
     * @return
     */
    String generateXfccHeaderFromSSO() {
        return generateXfccHeader("istio-system");
    }

    /**
     * Private helper to create a JWT on the fly
     * @param email email to embed with the "email" claim
     * @return the bearer token
     */
    String createToken(String email) {
        Algorithm algorithm = Algorithm.HMAC256("secret");
        return "Bearer " + JWT.create()
                .withIssuer("istio")
                .withClaim("email", email)
                .sign(algorithm);
    }
}
