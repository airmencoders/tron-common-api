package mil.tron.commonapi.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import mil.tron.commonapi.JwtUtils;
import mil.tron.commonapi.dto.DashboardUserDto;
import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.dto.PrivilegeDto;
import mil.tron.commonapi.dto.appclient.AppClientUserDto;
import mil.tron.commonapi.dto.appsource.AppEndpointDto;
import mil.tron.commonapi.dto.appsource.AppSourceDetailsDto;
import mil.tron.commonapi.dto.pubsub.SubscriberDto;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.PersonRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import mil.tron.commonapi.service.AppClientUserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = { "security.enabled=true", "efa-enabled=false" })
@ActiveProfiles(value = { "development", "test" })  // enable at least dev so we get tracing enabled for full integration
@AutoConfigureMockMvc
public class AppClientIntegrationTest {

    private static final String ENDPOINT = "/v1/app-client/";
    private static final String APP_SOURCE_ENDPOINT = "/v1/app-source/";
    private static final String DASHBOARD_USERS_ENDPOINT = "/v1/dashboard-users/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();


    @Autowired
    AppClientUserServiceImpl appClientService;

    @Autowired
    AppClientUserRespository appClientUserRespository;

    @Autowired
    PrivilegeRepository privRepo;
    
    @Autowired
    PersonRepository personRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DashboardUserRepository dashRepo;

    private DashboardUser admin;

    @BeforeEach
    @WithMockUser(username = "istio-system", authorities = "{ DASHBOARD_ADMIN }")
    void setup() throws Exception {

        // create the admin
        admin = DashboardUser.builder()
                .id(UUID.randomUUID())
                .email("admin@admin.com")
                .privileges(Set.of(
                        privRepo.findByName("DASHBOARD_ADMIN").orElseThrow(() -> new RecordNotFoundException("No DASH_BOARD ADMIN")),
                        privRepo.findByName("DASHBOARD_USER").orElseThrow(() -> new RecordNotFoundException("No DASH_BOARD USER"))
                ))
                .build();

        // persist the admin
        dashRepo.save(admin);
    }

    @Transactional
    @Rollback
    @Test
    void appClientDeveloperFullStackTests() throws Exception {

        // create two AppClient applications as a Dashboard_Admin - App1 and App2
        // assign App1 two admins - the existing Dashboard_Admin "admin@admin.com" (gets +APP_CLIENT_DEVELOPER priv) and a new user "user1@test.com"
        // assign App2 an admin - a new user "user2@test.com"
        // add new admin "user3@test.com" to App1 via a PUT (just adding an email to the developers field)
        // re-PUT App1 but without "user4@test.com", app1 should then just have 3 admins left on it
        // check that "user4@test.com" doesn't exist anywhere anymore since its not assigned to anything and he didnt have other privs
        // delete App1 -- check that "user3@test.com" and "user1@test.com" exist no where and that the Dashboard_Admin still exists in the system
        //   but no longer has an APP_CLIENT_DEVELOPER priv to him, since he was not an APP_CLIENT_DEVELOPER of anything else

        final String USER1_EMAIL = "user1@test.com";
        final String USER2_EMAIL = "user2@test.com";
        final String USER3_EMAIL = "user3@test.com";
        final String USER4_EMAIL = "user4@test.com";

        AppClientUserDto app1 = AppClientUserDto.builder()
                .id(UUID.randomUUID())
                .name("App1")
                .appClientDeveloperEmails(Lists.newArrayList(admin.getEmail(), USER1_EMAIL))
                .build();

        MvcResult app1Result = mockMvc.perform(post(ENDPOINT)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(app1)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID app1Id = OBJECT_MAPPER.readValue(app1Result.getResponse().getContentAsString(), AppClientUserDto.class).getId();
        app1.setId(app1Id);

        // verify admin user has the APP_CLIENT_DEVELOPER priv, and that user1@test.com exists now
        MvcResult result = mockMvc.perform(get(DASHBOARD_USERS_ENDPOINT)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andReturn();

        DashboardUserDto[] users = OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), DashboardUserDto[].class);

        boolean foundAdminsPriv = false;
        boolean foundUser1Priv = false;
        for (DashboardUserDto d : users) {
            if (d.getEmail().equalsIgnoreCase(admin.getEmail())) {
                for (PrivilegeDto p : d.getPrivileges()) {
                    if (p.getName().equals("DASHBOARD_ADMIN")) {
                        foundAdminsPriv = true;
                        break;
                    }
                }
            }
            else if (d.getEmail().equalsIgnoreCase(USER1_EMAIL)) {
                for (PrivilegeDto p : d.getPrivileges()) {
                    if (p.getName().equals("APP_CLIENT_DEVELOPER")) {
                        foundUser1Priv = true;
                        break;
                    }
                }
            }
        }
        assertTrue(foundAdminsPriv);
        assertTrue(foundUser1Priv);

        // make app2
        AppClientUserDto app2 = AppClientUserDto.builder()
                .id(UUID.randomUUID())
                .name("App2")
                .appClientDeveloperEmails(Lists.newArrayList(USER2_EMAIL))
                .build();

        MvcResult app2Result = mockMvc.perform(post(ENDPOINT)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(app2)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID app2Id = OBJECT_MAPPER.readValue(app2Result.getResponse().getContentAsString(), AppClientUserDto.class).getId();
        app2.setId(app2Id);

        mockMvc.perform(get(ENDPOINT + "{id}", app2Id)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(USER2_EMAIL))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appClientDeveloperEmails[0]", equalTo(USER2_EMAIL)));

        // user1 can't see app2's details
        mockMvc.perform(get(ENDPOINT + "{id}", app2Id)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(USER1_EMAIL))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isForbidden());

        // add user3 via PUT in app1 with user1's creds
        app1.getAppClientDeveloperEmails().add(USER3_EMAIL);
        app1.setId(app1Id);
        MvcResult modApp1 = mockMvc.perform(put(ENDPOINT + "{id}", app1Id)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(USER1_EMAIL))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(app1)))
                .andExpect(status().isOk())
                .andReturn();

        AppClientUserDto dto = OBJECT_MAPPER.readValue(modApp1.getResponse().getContentAsString(), AppClientUserDto.class);
        assertThat(dto.getAppClientDeveloperEmails().size()).isEqualTo(3);

        // try some random PUT as a non-app developer user - should get 403
        mockMvc.perform(put(ENDPOINT + "{id}", app1Id)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken("random@test.com"))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(app1)))
                .andExpect(status().isForbidden());

        app2.getAppClientDeveloperEmails().add(USER4_EMAIL);
        MvcResult modApp2 = mockMvc.perform(put(ENDPOINT + "{id}", app2Id)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(app2)))
                .andExpect(status().isOk())
                .andReturn();

        // verify that app2 got user4 - should have 2 admins
        AppClientUserDto dto2 = OBJECT_MAPPER.readValue(modApp2.getResponse().getContentAsString(), AppClientUserDto.class);
        assertThat(dto2.getAppClientDeveloperEmails().size()).isEqualTo(2);

        // delete "user4@test.com" from App2 via the PUT endpoint
        app2.getAppClientDeveloperEmails().remove(USER4_EMAIL);
        mockMvc.perform(put(ENDPOINT + "{id}", app2Id)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(app2)))
                .andExpect(status().isOk());

        // make sure that user4 is no longer anywhere in the system since it was a lone app client developer
        //  with no other privs and wasn't a developer anywhere else anymore :(
        // verify user has the APP_CLIENT_DEVELOPER priv, and that user1@test.com exists now
        result = mockMvc.perform(get(DASHBOARD_USERS_ENDPOINT)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andReturn();

        users = OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), DashboardUserDto[].class);

        boolean foundUser4Priv = false;
        for (DashboardUserDto d : users) {
            if (d.getEmail().equalsIgnoreCase(USER4_EMAIL)) {
                for (PrivilegeDto p : d.getPrivileges()) {
                    if (p.getName().equals("APP_CLIENT_DEVELOPER")) {
                        foundUser4Priv = true;
                        break;
                    }
                }
            }
        }
        assertFalse(foundUser4Priv);

        // delete App1
        mockMvc.perform(delete(ENDPOINT + "{id}", app1Id)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // check that "user3@test.com" and "user1@test.com" exist no where and that "admin@admin.com" still exists in the system
        result = mockMvc.perform(get(DASHBOARD_USERS_ENDPOINT)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andReturn();

        users = OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), DashboardUserDto[].class);

        boolean foundUser3 = false;
        boolean foundUser1 = false;
        boolean foundAdminUser = false;
        boolean adminHasNoMoreAppClientAdmin = true;
        for (DashboardUserDto d : users) {
            if (d.getEmail().equalsIgnoreCase(USER3_EMAIL)) {
                foundUser3 = true;
            }
            else if (d.getEmail().equalsIgnoreCase(USER1_EMAIL)) {
                foundUser1 = true;
            }
            else if (d.getEmail().equalsIgnoreCase(admin.getEmail())) {
                foundAdminUser = true;

                // make sure admin does have the APP_CLIENT_DEVELOPER anymore, not needed
                for (PrivilegeDto p : d.getPrivileges()) {
                    if (p.getName().equalsIgnoreCase("APP_CLIENT_DEVELOPER")) {
                        adminHasNoMoreAppClientAdmin = false;
                        break;
                    }
                }
            }
        }
        assertFalse(foundUser3);
        assertFalse(foundUser1);
        assertTrue(foundAdminUser);
        assertTrue(adminHasNoMoreAppClientAdmin);

        // re-create app1, and re-add users to app1 and then delete user1's dashboard user record, should delete them
        //  as an app client developer and a dashboard user
        app1Result = mockMvc.perform(post(ENDPOINT)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(app1)))
                .andExpect(status().isCreated())
                .andReturn();

        app1Id = OBJECT_MAPPER.readValue(app1Result.getResponse().getContentAsString(), AppClientUserDto.class).getId();
        app1.setId(app1Id);

        mockMvc.perform(put(ENDPOINT + "{id}", app1Id)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(app1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appClientDeveloperEmails[?(@ == '" + USER1_EMAIL + "')]", hasSize(1)));

        // find user1's dashboard user id
        result = mockMvc.perform(get(DASHBOARD_USERS_ENDPOINT)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andReturn();

        users = OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), DashboardUserDto[].class);

        UUID user1Id = null;
        for (DashboardUserDto d : users) {
            if (d.getEmail().equalsIgnoreCase(USER1_EMAIL)) {
                user1Id = d.getId();
            }
        }

        assertNotNull(user1Id);

        // delete user1 as a dashboard user, should take care of deleting them from app1 in the process
        mockMvc.perform(delete(DASHBOARD_USERS_ENDPOINT + "{id}", user1Id)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ENDPOINT + "{id}", app1Id)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appClientDeveloperEmails[?(@ == '" + USER1_EMAIL + "')]", hasSize(0)));
    }

    @Transactional
    @Rollback
    @Test
    void testSubscriptionPurgeOnAppClientDelete() throws Exception {

        // test that subscriptions for a given app client are purged when
        //  that app client is deleted
        ModelMapper mapper = new ModelMapper();

        AppClientUserDto app1 = AppClientUserDto.builder()
                .id(UUID.randomUUID())
                .name("App1")
                .appClientDeveloperEmails(Lists.newArrayList(admin.getEmail()))
                .privileges(Lists.newArrayList(
                        mapper.map(privRepo.findAll()
                                .stream()
                                .filter(item -> item.getName().equals("PERSON_READ"))
                                .findFirst()
                                .get(), PrivilegeDto.class)))
                .build();

        MvcResult result = mockMvc.perform(post(ENDPOINT)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(app1)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID id = OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), AppClientUserDto.class).getId();

        mockMvc.perform(post("/v2/subscriptions")
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(SubscriberDto
                    .builder()
                    .secret("blah")
                    .appClientUser("App1")
                    .subscriberAddress("/user")
                    .subscribedEvent(EventType.PERSON_CHANGE)
                    .build())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v2/subscriptions")
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        mockMvc.perform(delete(ENDPOINT + "{id}",id)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v2/subscriptions")
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Transactional
    @Rollback
    @Test
    void testAddingAndRemovingAppClientsWhenAppSourceExists() throws Exception {
        // Test that App Clients, when added, modify the existing App Source if one exists rather than 409
        // Test that App Clients, when deleted, modify the existing App Source if one exists rather than deleting the App object entirely
        String user1Email = "user1@test.com";

        AppSourceDetailsDto appSource = AppSourceDetailsDto.builder()
                .name("App1")
                .endpoints(Arrays.asList(AppEndpointDto.builder()
                        .id(UUID.randomUUID())
                        .path("/path")
                        .requestType("GET")
                        .deleted(false)
                        .build())
                )
                .build();

        MvcResult appSourceResult = mockMvc.perform(post(APP_SOURCE_ENDPOINT)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(appSource)))
                .andExpect(status().isCreated())
                .andReturn();
        
        UUID app1Id = OBJECT_MAPPER.readValue(appSourceResult.getResponse().getContentAsString(), AppSourceDetailsDto.class).getId();
        appSource.setId(app1Id);

        appSourceResult = mockMvc.perform(get(APP_SOURCE_ENDPOINT + "{id}", app1Id)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andReturn();

        // Verify adding a new App Client with the same name is attached to the same App row (Id)
        AppClientUserDto appClient = AppClientUserDto.builder()
                .id(UUID.randomUUID())
                .name("App1")
                .appClientDeveloperEmails(Lists.newArrayList(admin.getEmail(), user1Email))
                .build();

        MvcResult clientResult = mockMvc.perform(post(ENDPOINT)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(appClient)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID resultId = OBJECT_MAPPER.readValue(clientResult.getResponse().getContentAsString(), AppClientUserDto.class).getId();
        assertEquals(app1Id, resultId);
        appClient.setId(app1Id);

        // verify data has been set
        MvcResult result = mockMvc.perform(get(DASHBOARD_USERS_ENDPOINT)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andReturn();

        DashboardUserDto[] users = OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), DashboardUserDto[].class);
        assertThat(Arrays.asList(users).stream().anyMatch(item -> item.getEmail().equalsIgnoreCase(user1Email))).isTrue();

        // delete App1
        mockMvc.perform(delete(ENDPOINT + "{id}", app1Id)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());

        // check that "user3@test.com" and "user1@test.com" exist no where and that "admin@admin.com" still exists in the system
        result = mockMvc.perform(get(DASHBOARD_USERS_ENDPOINT)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andReturn();

        users = OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), DashboardUserDto[].class);
        assertThat(Arrays.asList(users).stream().anyMatch(item -> item.getEmail().equalsIgnoreCase(user1Email))).isFalse();

        // check that the App Source still exists
        appSourceResult = mockMvc.perform(get(APP_SOURCE_ENDPOINT + "{id}", app1Id)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(admin.getEmail()))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andReturn();

        AppSourceDetailsDto appSourceDetailsResult = OBJECT_MAPPER.readValue(appSourceResult.getResponse().getContentAsString(), AppSourceDetailsDto.class);
        assertEquals(appSource.getId(), appSourceDetailsResult.getId());
        assertEquals(appSource.getName(), appSourceDetailsResult.getName());
    }

    @Test
    @Transactional
    @Rollback
    void testCantUpdateSelfThroughAppClient() throws Exception {

        // test we can't do the /person/self PUT update when coming from an 
        //  app client - even if request has a JWT that matches that person
        //  they need to do it from the Dashboard

        Person p = Person.builder()
                .email("person@tron.mil")
                .build();
        
        personRepository.save(p);

        AppClientUser app2 = AppClientUser.builder()
                .name("App2")
                .privileges(Sets.newHashSet(privRepo.findByName("PERSON_READ").get(),
                    privRepo.findByName("PERSON_CREATE").get()))
                .build();

        appClientUserRespository.save(app2);

        PersonDto dto = PersonDto.builder()
                .id(p.getId())
                .email("person@tron.mil")
                .build();
        
        // try to update firstname but from the App2 app client
        dto.setFirstName("Bob");        
        mockMvc.perform(put("/v2/person/self/{id}", dto.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken("person@tron.mil"))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeader("App2"))
                .content(OBJECT_MAPPER.writeValueAsString(dto)))
                .andExpect(status().isForbidden());

        // test the update from the SSO works
        mockMvc.perform(put("/v2/person/self/{id}", dto.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken("person@tron.mil"))
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .content(OBJECT_MAPPER.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName", equalTo("Bob")));
    }

}
