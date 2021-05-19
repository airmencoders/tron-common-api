package mil.tron.commonapi.integration;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import mil.tron.commonapi.dto.DashboardUserDto;
import mil.tron.commonapi.dto.PrivilegeDto;
import mil.tron.commonapi.dto.appclient.AppClientUserDto;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import mil.tron.commonapi.service.AppClientUserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = { "security.enabled=true" })
@ActiveProfiles(value = { "development", "test" })  // enable at least dev so we get tracing enabled for full integration
@AutoConfigureMockMvc
public class AppClientIntegrationTest {

    private static final String XFCC_HEADER_NAME = "x-forwarded-client-cert";
    private static final String AUTH_HEADER_NAME = "authorization";
    private static final String NAMESPACE = "istio-system";
    private static final String XFCC_BY = "By=spiffe://cluster/ns/tron-common-api/sa/default";
    private static final String XFCC_H = "FAKE_H=12345";
    private static final String XFCC_SUBJECT = "Subject=\\\"\\\";";
    private static final String XFCC_HEADER = new StringBuilder()
            .append(XFCC_BY)
            .append(XFCC_H)
            .append(XFCC_SUBJECT)
            .append("URI=spiffe://cluster.local/ns/" + NAMESPACE + "/sa/default")
            .toString();

    private static final String ENDPOINT = "/v1/app-client/";
    private static final String DASHBOARD_USERS_ENDPOINT = "/v1/dashboard-users/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Private helper to create a JWT on the fly
     * @param email email to embed with the "email" claim
     * @return the bearer token
     */
    private String createToken(String email) {
        Algorithm algorithm = Algorithm.HMAC256("secret");
        return "Bearer " + JWT.create()
                .withIssuer("istio")
                .withClaim("email", email)
                .sign(algorithm);
    }

    @Autowired
    AppClientUserServiceImpl appClientService;

    @Autowired
    AppClientUserRespository appClientUserRespository;

    @Autowired
    PrivilegeRepository privRepo;

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
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(app1)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID app1Id = OBJECT_MAPPER.readValue(app1Result.getResponse().getContentAsString(), AppClientUserDto.class).getId();

        // verify admin user has the APP_CLIENT_DEVELOPER priv, and that user1@test.com exists now
        MvcResult result = mockMvc.perform(get(DASHBOARD_USERS_ENDPOINT)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
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
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(app2)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID app2Id = OBJECT_MAPPER.readValue(app2Result.getResponse().getContentAsString(), AppClientUserDto.class).getId();

        mockMvc.perform(get(ENDPOINT + "{id}", app2Id)
                .header(AUTH_HEADER_NAME, createToken(USER2_EMAIL))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appClientDeveloperEmails[0]", equalTo(USER2_EMAIL)));

        // user1 can't see app2's details
        mockMvc.perform(get(ENDPOINT + "{id}", app2Id)
                .header(AUTH_HEADER_NAME, createToken(USER1_EMAIL))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isForbidden());

        // add user3 via PUT in app1 with user1's creds
        app1.getAppClientDeveloperEmails().add(USER3_EMAIL);
        app1.setId(app1Id);
        MvcResult modApp1 = mockMvc.perform(put(ENDPOINT + "{id}", app1Id)
                .header(AUTH_HEADER_NAME, createToken(USER1_EMAIL))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(app1)))
                .andExpect(status().isOk())
                .andReturn();

        AppClientUserDto dto = OBJECT_MAPPER.readValue(modApp1.getResponse().getContentAsString(), AppClientUserDto.class);
        assertThat(dto.getAppClientDeveloperEmails().size()).isEqualTo(3);

        // try some random PUT as a non-app developer user - should get 403
        mockMvc.perform(put(ENDPOINT + "{id}", app1Id)
                .header(AUTH_HEADER_NAME, createToken("random@test.com"))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(app1)))
                .andExpect(status().isForbidden());

        app2.getAppClientDeveloperEmails().add(USER4_EMAIL);
        MvcResult modApp2 = mockMvc.perform(put(ENDPOINT + "{id}", app2Id)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
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
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(app2)))
                .andExpect(status().isOk());

        // make sure that user4 is no longer anywhere in the system since it was a lone app client developer
        //  with no other privs and wasn't a developer anywhere else anymore :(
        // verify user has the APP_CLIENT_DEVELOPER priv, and that user1@test.com exists now
        result = mockMvc.perform(get(DASHBOARD_USERS_ENDPOINT)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
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
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk());

        // check that "user3@test.com" and "user1@test.com" exist no where and that "admin@admin.com" still exists in the system
        result = mockMvc.perform(get(DASHBOARD_USERS_ENDPOINT)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
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
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(app1)))
                .andExpect(status().isCreated())
                .andReturn();

        app1Id = OBJECT_MAPPER.readValue(app1Result.getResponse().getContentAsString(), AppClientUserDto.class).getId();

        mockMvc.perform(put(ENDPOINT + "{id}", app1Id)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(app1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appClientDeveloperEmails[?(@ == '" + USER1_EMAIL + "')]", hasSize(1)));

        // find user1's dashboard user id
        result = mockMvc.perform(get(DASHBOARD_USERS_ENDPOINT)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
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
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ENDPOINT + "{id}", app1Id)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appClientDeveloperEmails[?(@ == '" + USER1_EMAIL + "')]", hasSize(0)));
    }



}
