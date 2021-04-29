package mil.tron.commonapi.integration;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import lombok.val;
import mil.tron.commonapi.dto.AppClientSummaryDto;
import mil.tron.commonapi.dto.AppClientUserPrivDto;
import mil.tron.commonapi.dto.DashboardUserDto;
import mil.tron.commonapi.dto.appsource.AppEndPointPrivDto;
import mil.tron.commonapi.dto.appsource.AppEndpointDto;
import mil.tron.commonapi.dto.appsource.AppSourceDetailsDto;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.appsource.AppEndpoint;
import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import mil.tron.commonapi.repository.appsource.AppEndpointPrivRepository;
import mil.tron.commonapi.repository.appsource.AppEndpointRepository;
import mil.tron.commonapi.repository.appsource.AppSourceRepository;
import mil.tron.commonapi.service.AppSourceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.transaction.Transactional;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = { "security.enabled=true" })
@TestPropertySource(locations = "classpath:application-test.properties")
@AutoConfigureMockMvc
public class AppSourceIntegrationTest {

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

    private static final String ENDPOINT = "/v1/app-source/";
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
    AppSourceServiceImpl appSourceServiceImpl;

    @Autowired
    AppEndpointPrivRepository appSourcePrivRepository;

    @Autowired
    AppEndpointRepository endpointRepository;

    @Autowired
    AppSourceRepository appSourceRepository;

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
    void testCreateAppSource() {
        val appClientUserUuid = UUID.randomUUID();
        appClientUserRespository.save(
                AppClientUser.builder()
                        .id(appClientUserUuid)
                        .name("App User 1")
                        .build()
        );
        AppEndpointDto appEndpointDto = AppEndpointDto.builder()
                .id(UUID.randomUUID())
                .path("/path")
                .requestType(RequestMethod.GET.toString())
                .build();
        List<AppClientUserPrivDto> privDtos = new ArrayList<>();
        privDtos.add(
                AppClientUserPrivDto
                        .builder()
                        .appClientUser(appClientUserUuid)
                        .appClientUserName("App User 1")
                        .appEndpoint(appEndpointDto.getId())
                        .privilege(appEndpointDto.getPath())
                        .build()
        );
        AppSourceDetailsDto appSource = AppSourceDetailsDto.builder()
                .name("Name")
                .appClients(privDtos)
                .endpoints(Arrays.asList(appEndpointDto))
                .build();
        appSourceServiceImpl.createAppSource(appSource);
        val appSources = appSourceServiceImpl.getAppSources();
        assertEquals(1, appSources.size());
    }

    @Transactional
    @Rollback
    @Test
    void testCreateAppSourceFromEndpoint() throws Exception {

        val appSource = AppSourceDetailsDto.builder()

                .name("App Source Test")
                .build();

        mockMvc.perform(post(ENDPOINT)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(appSource)))
                .andExpect(status().isCreated());
    }

    // test bad permission id
    @Transactional
    @Rollback
    @Test
    void testBadPermissionId() throws Exception {

        val appClientId = UUID.randomUUID();
        AppClientUser testAppClient = AppClientUser.builder()
                .id(appClientId)
                .name("Test App Client")
                .build();
        appClientUserRespository.save(testAppClient);
        val appSource = AppSourceDetailsDto.builder()
                .name("App Source Test")
                .appClients(Arrays.asList(AppClientUserPrivDto.builder()
                        .appClientUser(appClientId)
                        .appClientUserName("Test App Client")
                        .appEndpoint(UUID.randomUUID())
                        .privilege(ENDPOINT)
                        .build()))
                .endpoints(Arrays.asList(AppEndpointDto.builder()
                        .path(ENDPOINT)
                        .requestType("GET")
                        .build()))
                .build();

        val result = mockMvc.perform(post(ENDPOINT)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(appSource)))
                .andExpect(status().is4xxClientError())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        Assert.hasText("permission", content);
    }

    // test bad app client id
    @Transactional
    @Rollback
    @Test
    void testBadAppClientId() throws Exception {

        val appClientId = UUID.randomUUID();
        val appSource = AppSourceDetailsDto.builder()
                .name("App Source Test")
                .appClients(Arrays.asList(AppClientUserPrivDto.builder()
                        .appClientUser(appClientId)
                        .appClientUserName("Test App Client")
                        .appEndpoint(UUID.randomUUID())
                        .privilege(ENDPOINT)
                        .build()))
                .endpoints(Arrays.asList(AppEndpointDto.builder()
                        .path(ENDPOINT)
                        .requestType("GET")
                        .build()))
                .build();

        val result = mockMvc.perform(post(ENDPOINT)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(appSource)))
                .andExpect(status().is4xxClientError())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        Assert.hasText("app client", content);
    }

    @Transactional
    @Rollback
    @Test
    void successfulCreateRequest() throws Exception {

        val appClientId = UUID.randomUUID();
        val appEndpointId = UUID.randomUUID();
        AppClientUser testAppClient = AppClientUser.builder()
                .id(appClientId)
                .name("Test App Client")
                .build();
        appClientUserRespository.save(testAppClient);
        val appSource = AppSourceDetailsDto.builder()
                .name("App Source Test")
                .appClients(Arrays.asList(AppClientUserPrivDto.builder()
                        .appClientUser(appClientId)
                        .appEndpoint(appEndpointId)
                        .build()))
                .endpoints(Arrays.asList(AppEndpointDto.builder()
                        .id(appEndpointId)
                        .path("/path")
                        .requestType(RequestMethod.GET.toString())
                        .build()
                ))
                .build();

        mockMvc.perform(post(ENDPOINT)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(appSource)))
                .andExpect(status().isCreated());
    }

    @Transactional
    @Rollback
    @Test
    void getDetailsRequestWithInvalidId() throws Exception {

        val appClientId = UUID.randomUUID();

        mockMvc.perform(get(ENDPOINT + "{id}", appClientId)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());
    }

    @Transactional
    @Rollback
    @Test
    void successfulUpdateRequest() throws Exception {

        val appClientId = UUID.randomUUID();
        val endpointId = UUID.randomUUID();
        AppClientUser testAppClient = AppClientUser.builder()
                .id(appClientId)
                .name("Test App Client")
                .build();
        appClientUserRespository.save(testAppClient);
        val appSource = AppSourceDetailsDto.builder()
                .name("App Source Test")
                .appClients(Arrays.asList(AppClientUserPrivDto.builder()
                        .appClientUser(appClientId)
                        .appEndpoint(endpointId)
                        .build()))
                .endpoints(Arrays.asList(AppEndpointDto.builder()
                        .id(endpointId)
                        .path("/path")
                        .requestType(RequestMethod.GET.toString())
                        .build()
                ))
                .build();
        val createdAppSource = appSourceServiceImpl.createAppSource(appSource);

        System.out.println(OBJECT_MAPPER.writeValueAsString(appSource));
        createdAppSource.setName("New App Source Name");
        mockMvc.perform(put(ENDPOINT + "/{id}", createdAppSource.getId())
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(appSource)))
                .andExpect(status().isOk());

        val storedAppSourceResponse = appSourceRepository.findById(createdAppSource.getId());
        assertTrue(storedAppSourceResponse.isPresent());
        val storedAppSource = storedAppSourceResponse.get();
        assertEquals("New App Source Name", storedAppSource.getName());
    }

    @Transactional
    @Rollback
    @Test
    void successfulDeleteRequest() throws Exception {

        val appClientId = UUID.randomUUID();
        val appEndpointId = UUID.randomUUID();
        AppClientUser testAppClient = AppClientUser.builder()
                .id(appClientId)
                .name("Test App Client")
                .build();
        appClientUserRespository.save(testAppClient);
        val appClient2Id = UUID.randomUUID();
        AppClientUser testAppClient2 = AppClientUser.builder()
                .id(appClient2Id)
                .name("Test App Client 2")
                .build();
        appClientUserRespository.save(testAppClient2);
        val appSource = AppSourceDetailsDto.builder()
                .name("App Source Test")
                .appClients(Arrays.asList(
                    AppClientUserPrivDto.builder()
                        .appClientUser(appClientId)
                        .appEndpoint(appEndpointId)
                        .build(),
                    AppClientUserPrivDto.builder()
                            .appClientUser(appClient2Id)
                            .appEndpoint(appEndpointId)
                            .build()
                ))
                .endpoints(Arrays.asList(
                        AppEndpointDto.builder()    
                                .id(appEndpointId)
                                .path("/path")                            
                                .requestType(RequestMethod.GET.toString())
                                .build()
                ))
                .build();
        val createdAppSource = appSourceServiceImpl.createAppSource(appSource);

        mockMvc.perform(delete(ENDPOINT + "/{id}", createdAppSource.getId())
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(appSource)))
                .andExpect(status().isOk());

        val storedAppSourceResponse = appSourceRepository.findById(createdAppSource.getId());
        assertTrue(storedAppSourceResponse.isEmpty());
        val storedAppSourcePrivileges = appSourcePrivRepository
                .findAllByAppSource(AppSource.builder().id(createdAppSource.getId()).build());
        assertTrue(Iterables.size(storedAppSourcePrivileges) == 0);
    }

    @Transactional
    @Rollback
    @Test
    void appSourceAdminFullStackTests() throws Exception {

        // create two AppSource applications as a Dashboard_Admin - App1 and App2
        // assign App1 two admins - the existing Dashboard_Admin "admin@admin.com" (gets +APP_SOURCE_ADMIN priv) and a new user "user1@test.com"
        // assign App2 an admin - a new user "user2@test.com"
        // add new admin "user3@test.com" to App1 via a PUT (just adding an email to the admins collection field)
        // add new admin "user4@test.com" to both App1 and App2 via the PATCH endpoint calls
        // delete "user4@test.com" from App2 via the PATCH endpoint, check that "user4@test.com" is still on App1
        // re-PUT App1 but without "user4@test.com", app1 should then just have 3 admins left on it
        // check that "user4@test.com" doesn't exist anywhere anymore since its not assigned to anything and he didnt have other privs
        // delete App1 -- check that "user3@test.com" and "user1@test.com" exist no where and that the Dashboard_Admin still exists in the system
        //   but no longer has an APP_SOURCE_ADMIN priv to him, since he was not an APP_SOURCE_ADMIN of anything else

        final String USER1_EMAIL = "user1@test.com";
        final String USER2_EMAIL = "user2@test.com";
        final String USER3_EMAIL = "user3@test.com";
        final String USER4_EMAIL = "user4@test.com";

        AppSourceDetailsDto app1 = AppSourceDetailsDto.builder()
                .id(UUID.randomUUID())
                .name("App1")
                .appSourceAdminUserEmails(Lists.newArrayList(admin.getEmail(), USER1_EMAIL))
                .appClients(new ArrayList<>())
                .endpoints(new ArrayList<>())
                .build();

        MvcResult app1Result = mockMvc.perform(post(ENDPOINT)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(app1)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID app1Id = OBJECT_MAPPER.readValue(app1Result.getResponse().getContentAsString(), AppSourceDetailsDto.class).getId();

        // verify admin user has the APP_SOURCE_ADMIN priv, and that user1@test.com exists now
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
               for (Privilege p : d.getPrivileges()) {
                   if (p.getName().equals("APP_SOURCE_ADMIN")) {
                       foundAdminsPriv = true;
                       break;
                   }
               }
           }
           else if (d.getEmail().equalsIgnoreCase(USER1_EMAIL)) {
               for (Privilege p : d.getPrivileges()) {
                   if (p.getName().equals("APP_SOURCE_ADMIN")) {
                       foundUser1Priv = true;
                       break;
                   }
               }
           }
        }
        assertTrue(foundAdminsPriv);
        assertTrue(foundUser1Priv);

        // make app2
        AppSourceDetailsDto app2 = AppSourceDetailsDto.builder()
                .id(UUID.randomUUID())
                .name("App2")
                .appSourceAdminUserEmails(Lists.newArrayList(USER2_EMAIL))
                .appClients(new ArrayList<>())
                .endpoints(new ArrayList<>())
                .build();

        MvcResult app2Result = mockMvc.perform(post(ENDPOINT)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(app2)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID app2Id = OBJECT_MAPPER.readValue(app2Result.getResponse().getContentAsString(), AppSourceDetailsDto.class).getId();

        // add user3 via PUT in app1 with user1's creds
        app1.getAppSourceAdminUserEmails().add(USER3_EMAIL);
        app1.setId(app1Id);
        MvcResult modApp1 = mockMvc.perform(put(ENDPOINT + "{id}", app1Id)
                .header(AUTH_HEADER_NAME, createToken(USER1_EMAIL))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(app1)))
                .andExpect(status().isOk())
                .andReturn();

        AppSourceDetailsDto dto = OBJECT_MAPPER.readValue(modApp1.getResponse().getContentAsString(), AppSourceDetailsDto.class);
        assertThat(dto.getAppSourceAdminUserEmails().size()).isEqualTo(3);

        // try some random PUT as a non-app source admin user - should get 403
        mockMvc.perform(put(ENDPOINT + "{id}", app1Id)
                .header(AUTH_HEADER_NAME, createToken("random@test.com"))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(app1)))
                .andExpect(status().isForbidden());


        // add user4 via the PATCH endpoints to App1 and App2
        mockMvc.perform(patch(ENDPOINT + "admins/{id}", app1Id)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{ \"email\": \"%s\" }", USER4_EMAIL)))
                .andExpect(status().isOk());

        // check that "user4@test.com" is added to App1
        modApp1 = mockMvc.perform(get(ENDPOINT + "{id}", app1Id)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)         )
                .andExpect(status().isOk())
                .andReturn();

        dto = OBJECT_MAPPER.readValue(modApp1.getResponse().getContentAsString(), AppSourceDetailsDto.class);
        assertThat(dto.getAppSourceAdminUserEmails().size()).isEqualTo(4);

        MvcResult modApp2 = mockMvc.perform(patch(ENDPOINT + "admins/{id}", app2Id)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{ \"email\": \"%s\" }", USER4_EMAIL)))
                .andExpect(status().isOk())
                .andReturn();

        // verify that app2 got user4 - should have 2 admins
        AppSourceDetailsDto dto2 = OBJECT_MAPPER.readValue(modApp2.getResponse().getContentAsString(), AppSourceDetailsDto.class);
        assertThat(dto2.getAppSourceAdminUserEmails().size()).isEqualTo(2);

        // delete "user4@test.com" from App2 via the PATCH endpoint
        mockMvc.perform(delete(ENDPOINT + "admins/{id}", app2Id)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{ \"email\": \"%s\" }", USER4_EMAIL)))
                .andExpect(status().isOk());

        // check that "user4@test.com" is still on App1 (should have 4 admins)
        modApp1 = mockMvc.perform(get(ENDPOINT + "{id}", app1Id)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andReturn();

        dto = OBJECT_MAPPER.readValue(modApp1.getResponse().getContentAsString(), AppSourceDetailsDto.class);
        assertThat(dto.getAppSourceAdminUserEmails().size()).isEqualTo(4);

        // re-PUT app1 sans user4 this time -- effectively deleting them from app source admin of app1
        //   should have 3 admins back on it
        modApp1 = mockMvc.perform(put(ENDPOINT + "{id}", app1Id)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(app1)))
                .andExpect(status().isOk())
                .andReturn();

        dto = OBJECT_MAPPER.readValue(modApp1.getResponse().getContentAsString(), AppSourceDetailsDto.class);
        assertThat(dto.getAppSourceAdminUserEmails().size()).isEqualTo(3);

        // make sure that user4 is no longer anywhere in the system since it was a lone app source admin
        //  with no other privs and wasn't an admin anywhere else anymore :(
        // verify admin user has the APP_SOURCE_ADMIN priv, and that user1@test.com exists now
        result = mockMvc.perform(get(DASHBOARD_USERS_ENDPOINT)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andReturn();

        users = OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), DashboardUserDto[].class);

        boolean foundUser4Priv = false;
        for (DashboardUserDto d : users) {
            if (d.getEmail().equalsIgnoreCase(USER4_EMAIL)) {
                for (Privilege p : d.getPrivileges()) {
                    if (p.getName().equals("APP_SOURCE_ADMIN")) {
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
        boolean adminHasNoMoreAppSourceAdmin = true;
        for (DashboardUserDto d : users) {
            if (d.getEmail().equalsIgnoreCase(USER3_EMAIL)) {
                foundUser3 = true;
            }
            else if (d.getEmail().equalsIgnoreCase(USER1_EMAIL)) {
                foundUser1 = true;
            }
            else if (d.getEmail().equalsIgnoreCase(admin.getEmail())) {
                foundAdminUser = true;

                // make sure admin does have the app_source_admin anymore, not needed
                for (Privilege p : d.getPrivileges()) {
                    if (p.getName().equalsIgnoreCase("APP_SOURCE_ADMIN")) {
                        adminHasNoMoreAppSourceAdmin = false;
                        break;
                    }
                }
            }
        }
        assertFalse(foundUser3);
        assertFalse(foundUser1);
        assertTrue(foundAdminUser);
        assertTrue(adminHasNoMoreAppSourceAdmin);
    }

    @Transactional
    @Rollback
    @Test
    void testUsersCanOnlySeeAllowedAppSources() throws Exception {

        // test that DASHBOARD_ADMINS can see all app sources via REST call
        // test that APP SOURCE ADMINS can only see the apps they are admins of

        final String USER1_EMAIL = "user1@test.com";
        final String USER2_EMAIL = "user2@test.com";

        AppSourceDetailsDto app1 = AppSourceDetailsDto.builder()
                .id(UUID.randomUUID())
                .name("App1")
                .appSourceAdminUserEmails(Lists.newArrayList(USER1_EMAIL))
                .appClients(new ArrayList<>())
                .endpoints(new ArrayList<>())
                .build();

        mockMvc.perform(post(ENDPOINT)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(app1)))
                .andExpect(status().isCreated());

        AppSourceDetailsDto app2 = AppSourceDetailsDto.builder()
                .id(UUID.randomUUID())
                .name("App2")
                .appSourceAdminUserEmails(Lists.newArrayList(USER2_EMAIL))
                .appClients(new ArrayList<>())
                .endpoints(new ArrayList<>())
                .build();

        mockMvc.perform(post(ENDPOINT)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(app2)))
                .andExpect(status().isCreated());

        AppSourceDetailsDto app3 = AppSourceDetailsDto.builder()
                .id(UUID.randomUUID())
                .name("App3")
                .appSourceAdminUserEmails(Lists.newArrayList(admin.getEmail()))
                .appClients(new ArrayList<>())
                .endpoints(new ArrayList<>())
                .build();

        mockMvc.perform(post(ENDPOINT)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(app3)))
                .andExpect(status().isCreated());

        // verify if a DASHBOARD admin requests anything we get everything -  3 apps
        mockMvc.perform(get(ENDPOINT)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        // verify admin user has additional APP_SOURCE_ADMIN priv
        MvcResult result = mockMvc.perform(get(DASHBOARD_USERS_ENDPOINT)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andReturn();

        DashboardUserDto[] dtos = OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), DashboardUserDto[].class);
        boolean foundPriv = false;
        for (DashboardUserDto d : dtos) {
            if (d.getEmail().equalsIgnoreCase(admin.getEmail())) {
                for (Privilege p : d.getPrivileges()) {
                    if (p.getName().equalsIgnoreCase("APP_SOURCE_ADMIN")) {
                        foundPriv = true;
                        break;
                    }
                }
            }
        }
        assertTrue(foundPriv);

        // verify if a USER1 sees one record
        mockMvc.perform(get(ENDPOINT)
                .header(AUTH_HEADER_NAME, createToken(USER1_EMAIL))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // verify if a USER2 sees one record
        mockMvc.perform(get(ENDPOINT)
                .header(AUTH_HEADER_NAME, createToken(USER2_EMAIL))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Transactional
    @Rollback
    @Test
    void testAdministrationOfEndPoints() throws Exception {

        // create new AppSource application as a Dashboard_Admin - App1
        // assign App1 admin - new user "user1@test.com"

        final String USER1_EMAIL = "user1@test.com";

        AppClientUser client = AppClientUser.builder()
                .id(UUID.randomUUID())
                .name("Test Client")
                .build();

        appClientUserRespository.save(client);

        Privilege admin = privRepo.findByName("APP_SOURCE_ADMIN").get();

        DashboardUser user = DashboardUser.builder()
                .id(UUID.randomUUID())
                .email(USER1_EMAIL)
                .privileges(Set.of(admin))
                .build();

        dashRepo.save(user);

        AppSource app1Main = AppSource.builder()
                .id(UUID.randomUUID())
                .name("App1")
                .appSourceAdmins(Set.of(user))
                .appSourcePath("app1")
                .build();

        // mimic app that was already loaded at app invocation and camel processing is done
        appSourceRepository.saveAndFlush(app1Main);

        AppEndpoint endPoint = AppEndpoint
                .builder()
                .id(UUID.randomUUID())
                .appSource(app1Main)
                .method(RequestMethod.GET)
                .path("/all")
                .build();

        endpointRepository.save(endPoint);

        app1Main.setAppEndpoints(Set.of(endPoint));
        appSourceRepository.saveAndFlush(app1Main);

        MvcResult clientApps = mockMvc.perform(get(ENDPOINT + "app-clients")
                .header(AUTH_HEADER_NAME, createToken(USER1_EMAIL))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andReturn();

        AppClientSummaryDto[] availableClientApps = OBJECT_MAPPER
                .readValue(clientApps.getResponse().getContentAsString(), AppClientSummaryDto[].class);

        UUID clientAppId = null;
        for (AppClientSummaryDto d : availableClientApps) {
            if (d.getName().equals(client.getName())) {
                clientAppId = d.getId();
                break;
            }
        }
        assertNotNull(clientAppId);

        AppEndPointPrivDto privDto = AppEndPointPrivDto.builder()
                .appSourceId(app1Main.getId())
                .appEndpointId(endPoint.getId())
                .appClientUserId(clientAppId)
                .build();

        // now, ...finally, we can connect up "Test Client" with "App1" endpoint
        MvcResult result = mockMvc.perform(post(ENDPOINT + "app-clients")
                .header(AUTH_HEADER_NAME, createToken(USER1_EMAIL))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(privDto)))
                .andExpect(status().isCreated())
                .andReturn();

        // verify we can't add twice
        mockMvc.perform(post(ENDPOINT + "app-clients")
                .header(AUTH_HEADER_NAME, createToken(USER1_EMAIL))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(privDto)))
                .andExpect(status().isConflict());

        AppSourceDetailsDto appSourceRecord = OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), AppSourceDetailsDto.class);
        assertEquals(1, appSourceRecord.getAppClients().size());

        // verify we can delete all in one request
        MvcResult result2 = mockMvc.perform(delete(ENDPOINT + "app-clients/all/{id}", app1Main.getId())
                .header(AUTH_HEADER_NAME, createToken(USER1_EMAIL))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andReturn();

        AppSourceDetailsDto appSourceRecord2 = OBJECT_MAPPER.readValue(result2.getResponse().getContentAsString(), AppSourceDetailsDto.class);
        assertEquals(0, appSourceRecord2.getAppClients().size());

        // add it again
        MvcResult result3 = mockMvc.perform(post(ENDPOINT + "app-clients")
                .header(AUTH_HEADER_NAME, createToken(USER1_EMAIL))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(privDto)))
                .andExpect(status().isCreated())
                .andReturn();

        AppSourceDetailsDto appSourceRecord3 = OBJECT_MAPPER.readValue(result3.getResponse().getContentAsString(), AppSourceDetailsDto.class);
        assertEquals(1, appSourceRecord3.getAppClients().size());

        UUID privId = appSourceRecord3.getAppClients().get(0).getId();

        // singularly delete this time
        MvcResult result4 = mockMvc.perform(delete(ENDPOINT + "app-clients/{appId}/{privId}", app1Main.getId(), privId)
                .header(AUTH_HEADER_NAME, createToken(USER1_EMAIL))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andReturn();

        AppSourceDetailsDto appSourceRecord4 = OBJECT_MAPPER.readValue(result4.getResponse().getContentAsString(), AppSourceDetailsDto.class);
        assertEquals(0, appSourceRecord4.getAppClients().size());
    }

    @Transactional
    @Rollback
    @Test
    void testRemoveDashboardUserWhoAlsoIsAppSourceAdmin() throws Exception {

        DashboardUser newUser = DashboardUser.builder()
                .email("tester@test.com")
                .privileges(Set.of(
                        privRepo.findByName("DASHBOARD_ADMIN").orElseThrow(() -> new RecordNotFoundException("No DASH_BOARD ADMIN")),
                        privRepo.findByName("DASHBOARD_USER").orElseThrow(() -> new RecordNotFoundException("No DASH_BOARD USER"))
                ))
                .build();

        dashRepo.save(newUser);

        AppSourceDetailsDto app1 = AppSourceDetailsDto.builder()
                .id(UUID.randomUUID())
                .name("App1")
                .appSourceAdminUserEmails(Lists.newArrayList("tester@test.com"))
                .appClients(new ArrayList<>())
                .endpoints(new ArrayList<>())
                .build();

        mockMvc.perform(post(ENDPOINT)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(app1)))
                .andExpect(status().isCreated());


        // verify new user has the APP_SOURCE_ADMIN priv
        MvcResult result = mockMvc.perform(get(DASHBOARD_USERS_ENDPOINT)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andReturn();

        DashboardUserDto[] users = OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), DashboardUserDto[].class);

        boolean foundAdminsPriv = false;
        for (DashboardUserDto d : users) {
            if (d.getEmail().equalsIgnoreCase("tester@test.com")) {
                for (Privilege p : d.getPrivileges()) {
                    if (p.getName().equals("APP_SOURCE_ADMIN")) {
                        foundAdminsPriv = true;
                        break;
                    }
                }
            }
        }
        assertTrue(foundAdminsPriv);

        // now delete newUser as a dashboard user
        mockMvc.perform(delete(DASHBOARD_USERS_ENDPOINT + "{id}", newUser.getId())
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isNoContent());
    }
}
