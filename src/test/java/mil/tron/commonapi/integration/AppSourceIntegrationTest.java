package mil.tron.commonapi.integration;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import lombok.val;
import mil.tron.commonapi.CacheConfig;
import mil.tron.commonapi.appgateway.AppSourceConfig;
import mil.tron.commonapi.appgateway.AppSourceInterfaceDefinition;
import mil.tron.commonapi.dto.AppClientUserPrivDto;
import mil.tron.commonapi.dto.DashboardUserDto;
import mil.tron.commonapi.dto.PrivilegeDto;
import mil.tron.commonapi.dto.appclient.AppClientSummaryDto;
import mil.tron.commonapi.dto.appclient.AppClientSummaryDtoResponseWrapper;
import mil.tron.commonapi.dto.appsource.AppEndPointPrivDto;
import mil.tron.commonapi.dto.appsource.AppEndpointDto;
import mil.tron.commonapi.dto.appsource.AppSourceDetailsDto;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.appsource.AppEndpoint;
import mil.tron.commonapi.entity.appsource.AppEndpointPriv;
import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.health.AppSourceHealthIndicator;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.transaction.Transactional;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = { "security.enabled=true", "app-source-ping-rate-millis=100", "caching.enabled=true" })
@TestPropertySource(locations = "classpath:application-test.properties")
@ActiveProfiles(value = { "development", "test" })  // enable at least dev so we get tracing enabled for full integration
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
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
    private static final String ENDPOINT_V2 = "/v2/app-source/";
    private static final String DASHBOARD_USERS_ENDPOINT = "/v1/dashboard-users/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CACHE_NAME = "app_source_details_cache";
	
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
    
    @Autowired
    @Qualifier(CacheConfig.SERVICE_ENTITY_CACHE_MANAGER)
    CacheManager cacheManager;

    @Autowired
    private HealthContributorRegistry registry;

    @Autowired
    private AppSourceConfig appSourceConfig;

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
    void testNameMismatchWithSameAppSourcePath() throws Exception {
        val id = UUID.randomUUID();
        appSourceRepository.save(
                AppSource.builder()
                        .id(id)
                        .name("App_Source_22")
                        .nameAsLower("app_source_22")
                        .appSourcePath("app22")
                        .build()
        );

        AppSourceInterfaceDefinition def = AppSourceInterfaceDefinition.builder()
                .appSourcePath("app22")
                .name("New_App_Name")
                .build();

        ReflectionTestUtils.invokeMethod(appSourceConfig, "registerAppSource", def);
        AppSource source = appSourceRepository.getById(id);
        assertTrue(source.getName().equals("New_App_Name"));
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
               for (PrivilegeDto p : d.getPrivileges()) {
                   if (p.getName().equals("APP_SOURCE_ADMIN")) {
                       foundAdminsPriv = true;
                       break;
                   }
               }
           }
           else if (d.getEmail().equalsIgnoreCase(USER1_EMAIL)) {
               for (PrivilegeDto p : d.getPrivileges()) {
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
                for (PrivilegeDto p : d.getPrivileges()) {
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
                for (PrivilegeDto p : d.getPrivileges()) {
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
        mockMvc.perform(get(ENDPOINT_V2)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)));

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
                for (PrivilegeDto p : d.getPrivileges()) {
                    if (p.getName().equalsIgnoreCase("APP_SOURCE_ADMIN")) {
                        foundPriv = true;
                        break;
                    }
                }
            }
        }
        assertTrue(foundPriv);

        // verify if a USER1 sees one record
        mockMvc.perform(get(ENDPOINT_V2)
                .header(AUTH_HEADER_NAME, createToken(USER1_EMAIL))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        // verify if a USER2 sees one record
        mockMvc.perform(get(ENDPOINT_V2)
                .header(AUTH_HEADER_NAME, createToken(USER2_EMAIL))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
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

        MvcResult clientApps = mockMvc.perform(get(ENDPOINT_V2 + "app-clients")
                .header(AUTH_HEADER_NAME, createToken(USER1_EMAIL))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andReturn();

        List<AppClientSummaryDto> availableClientApps = OBJECT_MAPPER
                .readValue(clientApps.getResponse().getContentAsString(), AppClientSummaryDtoResponseWrapper.class).getData();

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
                for (PrivilegeDto p : d.getPrivileges()) {
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

    @Transactional
    @Rollback
    @Test
    void testGetApiSpecResource() throws Exception {
        UUID appSourceId = UUID.randomUUID();
        appSourceRepository.save(AppSource.builder()
                .id(appSourceId)
                .appSourcePath("path")
                .availableAsAppSource(true)
                .name("Name")
                // Note: This is actually pulling from src/main/resources/appsourceapis
                .openApiSpecFilename("mock.yml")
                .build());

        Resource resource = appSourceServiceImpl.getApiSpecForAppSource(appSourceId);

        assertNotNull(resource);
    }

    @Transactional
    @Rollback
    @Test
    void testGetApiSpecResourceByEndpointPriv() throws Exception {
        AppClientUser appClientUser = appClientUserRespository.save(
                AppClientUser.builder()
                        .id(UUID.randomUUID())
                        .name("App User 1")
                        .build()
        );

        AppSource appSource = appSourceRepository.save(AppSource.builder()
                .id(UUID.randomUUID())
                .appSourcePath("path")
                .appEndpoints(null)
                .availableAsAppSource(true)
                .name("Name")
                // Note: This is actually pulling from src/main/resources/appsourceapis
                .openApiSpecFilename("mock.yml")
                .build());

        AppEndpoint appEndpoint = endpointRepository.save(AppEndpoint.builder()
                .id(UUID.randomUUID())
                .path("/path")
                .method(RequestMethod.GET)
                .appSource(appSource)
                .build());
        
        UUID appEndpointPrivId = UUID.randomUUID();
        appSourcePrivRepository.save(AppEndpointPriv.builder()
                .id(appEndpointPrivId)
                .appClientUser(appClientUser)
                .appEndpoint(appEndpoint)
                .appSource(appSource)
                .build());

        Resource resource = appSourceServiceImpl.getApiSpecForAppSourceByEndpointPriv(appEndpointPrivId);

        assertNotNull(resource);
    }
    
	private Optional<AppSourceDetailsDto> getCachedAppSourceById(UUID id) {
        return Optional.ofNullable(cacheManager.getCache(CACHE_NAME)).map(c -> c.get(id, AppSourceDetailsDto.class));
    }
    	
	@Transactional
	@Rollback
	@Test
	void appSourceShouldBeCached() {
		AppSource testAppSource = AppSource.builder()
                    .id(UUID.randomUUID())
                    .name("Test App Source")
	                .appSourcePath("test_app_source")
	                .build();
			
		appSourceRepository.saveAndFlush(testAppSource);
		
		// Cache should be empty at this point
		assertThat(getCachedAppSourceById(testAppSource.getId())).isEmpty();
		
		// Call getAppSource() to save it into the cache
		assertThat(appSourceServiceImpl.getAppSource(testAppSource.getId())).isNotNull();
		
		// Check that this item is in the cache
		assertThat(testAppSource.getId()).hasToString(getCachedAppSourceById(testAppSource.getId()).get().getId().toString());
	}
	
	@Transactional
	@Rollback
	@Test
	void appSourceShouldBeCachedWhenCreated() {
		AppSourceDetailsDto testAppSource = AppSourceDetailsDto.builder()
                    .id(UUID.randomUUID())
                    .name("Test App Source")
	                .appSourcePath("test_app_source")
	                .build();
		
		// Cache should be empty at this point
		assertThat(getCachedAppSourceById(testAppSource.getId())).isEmpty();
		
		// Creating an App Source should put it into cache
		appSourceServiceImpl.createAppSource(testAppSource);

		// Check that this item is in the cache
		assertThat(getCachedAppSourceById(testAppSource.getId()).get().getId()).hasToString(testAppSource.getId().toString());
	}
	
	@Transactional
	@Rollback
	@Test
	void appSourceCacheShouldBeUpdatedWhenAppSourceUpdated() throws JsonProcessingException {
		UUID appClientId = UUID.randomUUID();
		UUID appEndpointId = UUID.randomUUID();
		
        AppClientUser testAppClient = AppClientUser.builder()
                .id(appClientId)
                .name("Test App Client")
                .build();
        appClientUserRespository.save(testAppClient);

        AppSourceDetailsDto testAppSource = AppSourceDetailsDto.builder()
                .name("App Source Test")
                .endpoints(Arrays.asList(
                        AppEndpointDto.builder()    
                                .id(appEndpointId)
                                .path("/path")                            
                                .requestType(RequestMethod.GET.toString())
                                .build()
                ))
                .appSourceAdminUserEmails(List.of("test@admin.com"))
                .build();
		
		// Cache should be empty at this point
		assertThat(getCachedAppSourceById(testAppSource.getId())).isEmpty();
		
		// Creating an App Source should put it into cache
		appSourceServiceImpl.createAppSource(testAppSource);
		
		// Check that this item is in the cache
		assertThat(getCachedAppSourceById(testAppSource.getId()).get().getId()).hasToString(testAppSource.getId().toString());
		
		// Update the item should also update the cache
		testAppSource.setAppSourcePath("new_app_source_path");
		appSourceServiceImpl.updateAppSource(testAppSource.getId(), testAppSource);
		
		// Check that the updated item is in the cache
		assertThat(getCachedAppSourceById(testAppSource.getId()).get().getAppSourcePath()).isEqualTo(testAppSource.getAppSourcePath());
		
		// Remove Admins and check cache is updated
		appSourceServiceImpl.removeAdminFromAppSource(testAppSource.getId(), "test@admin.com");
		assertThat(getCachedAppSourceById(testAppSource.getId()).get().getAppSourceAdminUserEmails()).doesNotContain("test@admin.com");
		
		// Add Admins and check cache is updated
		appSourceServiceImpl.addAppSourceAdmin(testAppSource.getId(), "test@admin.com");
		assertThat(getCachedAppSourceById(testAppSource.getId()).get().getAppSourceAdminUserEmails()).contains("test@admin.com");
		
		// Add endpoint privilege and check cache is updated
		AppEndPointPrivDto appEndpointPriv = AppEndPointPrivDto.builder()
				.appClientUserId(appClientId)
				.appSourceId(testAppSource.getId())
				.appEndpointId(appEndpointId)
				.build();
		appSourceServiceImpl.addEndPointPrivilege(appEndpointPriv);
		assertThat(getCachedAppSourceById(testAppSource.getId()).get().getAppClients()).hasSize(1);
		
		// Remove endpoint privilege and check cache is updated
		assertThat(getCachedAppSourceById(testAppSource.getId()).get().getAppClients()).hasSize(1);
		AppSource savedTestAppSource = appSourceRepository.findById(testAppSource.getId()).get();
		AppEndpointPriv[] appEndpointPrivs = new AppEndpointPriv[savedTestAppSource.getAppPrivs().size()];
		appEndpointPrivs = savedTestAppSource.getAppPrivs().toArray(appEndpointPrivs);
		appSourceServiceImpl.removeEndPointPrivilege(testAppSource.getId(), appEndpointPrivs[0].getId());
		assertThat(getCachedAppSourceById(testAppSource.getId()).get().getAppClients()).isEmpty();
		
		// Remove all app client privileges and check cache is updated
		appSourceServiceImpl.deleteAllAppClientPrivs(testAppSource.getId());
		assertThat(getCachedAppSourceById(testAppSource.getId()).get().getAppClients()).isEmpty();
		
		// Remove all Admins and check item removed from cache
		// Do this last because this will reset the entire cache due to using @CacheEvict
		savedTestAppSource = appSourceRepository.findById(testAppSource.getId()).get();
		DashboardUser[] dashboardUsers = new DashboardUser[savedTestAppSource.getAppSourceAdmins().size()];
		dashboardUsers = savedTestAppSource.getAppSourceAdmins().toArray(dashboardUsers);
		appSourceServiceImpl.deleteAdminFromAllAppSources(dashboardUsers[0]);
		assertThat(getCachedAppSourceById(testAppSource.getId())).isEmpty();
	}
	
	@Transactional
	@Rollback
	@Test
	void appSourceInCacheShouldBeRemovedWhenDeleted() {
		AppSourceDetailsDto testAppSource = AppSourceDetailsDto.builder()
                    .id(UUID.randomUUID())
                    .name("Test App Source")
	                .appSourcePath("test_app_source")
	                .build();
			
		// Cache should be empty at this point
		assertThat(getCachedAppSourceById(testAppSource.getId())).isEmpty();
		
		// Creating an App Source should put it into cache
		appSourceServiceImpl.createAppSource(testAppSource);
		
		// Check that this item is in the cache
		assertThat(getCachedAppSourceById(testAppSource.getId()).get().getId()).hasToString(testAppSource.getId().toString());
		
		// Delete the item
		appSourceServiceImpl.deleteAppSource(testAppSource.getId());
		
		// Check that this item does not exist in cache
		assertThat(getCachedAppSourceById(testAppSource.getId())).isEmpty();
	}

    @Test
    @Transactional
    @Rollback
    void testHealthChecks() throws Exception {
        appSourceConfig.addAppSourcePathToDefMapping("name", AppSourceInterfaceDefinition
                        .builder()
                        .appSourcePath("name")
                        .sourceUrl("http://localhost")
                        .name("Name")
                        .openApiSpecFilename("some.yaml")
                        .build());

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
                .reportStatus(true)
                .healthUrl("/healthz")
                .build();
        appSourceServiceImpl.createAppSource(appSource);
        val appSources = appSourceServiceImpl.getAppSources();
        assertEquals(1, appSources.size());

        // have to modify app source path in the db itself since that cant
        //  be set over the rest interface
        AppSource source = appSourceRepository.findByNameIgnoreCase("Name").get();
        source.setAppSourcePath("name");
        appSourceRepository.save(source);

        // force update over rest so health checks get registered
        appSourceServiceImpl.updateAppSource(appSource.getId(), appSource);

        mockMvc.perform(get("/actuator/health")
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.appsource_Name.status", equalTo("APPSOURCE_UNKNOWN")))
                .andExpect(jsonPath("$.components.appsource_Name.details.error", equalTo("Health check has not run yet")));

        int delayTime = 2000;
        Thread.sleep(delayTime);

        mockMvc.perform(get("/actuator/health")
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.appsource_Name.status", equalTo("APPSOURCE_DOWN")))
                .andExpect(jsonPath("$.components.appsource_Name.details.error", equalTo("Could not connect to health url")));

        AppSourceHealthIndicator indicator = (AppSourceHealthIndicator) registry.getContributor("appsource_Name");
        MockRestServiceServer mockRestServiceServer = MockRestServiceServer.createServer(indicator.getHealthSender());
        mockRestServiceServer.expect(manyTimes(), MockRestRequestMatchers.anything())
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        Thread.sleep(delayTime);
        mockMvc.perform(get("/actuator/health")
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.appsource_Name.status", equalTo("APPSOURCE_UP")))
                .andExpect(jsonPath("$.components.appsource_Name.details.['Last Up Time']", notNullValue()));

        mockRestServiceServer.reset();
        mockRestServiceServer.expect(manyTimes(), MockRestRequestMatchers.anything())
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        Thread.sleep(delayTime);

        mockMvc.perform(get("/actuator/health")
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.appsource_Name.status", equalTo("APPSOURCE_ERROR")))
                .andExpect(jsonPath("$.components.appsource_Name.details.['Last Up Time']", notNullValue()));

        mockRestServiceServer.reset();
        mockRestServiceServer.expect(manyTimes(), requestTo(endsWith("healthz")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        Thread.sleep(delayTime);

        mockMvc.perform(get("/actuator/health")
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.appsource_Name.status", equalTo("APPSOURCE_ERROR")))
                .andExpect(jsonPath("$.components.appsource_Name.details.['Last Up Time']", notNullValue()));

        ((AppSourceHealthIndicator) registry.unregisterContributor("appsource_Name")).cancelPing();

    }
}
