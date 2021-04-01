package mil.tron.commonapi.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.transaction.Transactional;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterables;

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
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestMethod;

import lombok.val;
import mil.tron.commonapi.dto.AppClientUserPrivDto;
import mil.tron.commonapi.dto.appsource.AppEndpointDto;
import mil.tron.commonapi.dto.appsource.AppSourceDetailsDto;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import mil.tron.commonapi.repository.appsource.AppEndpointPrivRepository;
import mil.tron.commonapi.repository.appsource.AppSourceRepository;
import mil.tron.commonapi.service.AppSourceServiceImpl;

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
    AppSourceRepository appSourceRepository;

    @Autowired
    AppClientUserRespository appClientUserRespository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PrivilegeRepository privRepo;

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
                .privileges(Set.of(privRepo.findByName("DASHBOARD_ADMIN").orElseThrow(() -> new RecordNotFoundException("No DASH_BOARD ADMIN"))))
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
        val testAppClient = AppClientUser.builder()
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
        val testAppClient = AppClientUser.builder()
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
        val testAppClient = AppClientUser.builder()
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
        val testAppClient = AppClientUser.builder()
                .id(appClientId)
                .name("Test App Client")
                .build();
        appClientUserRespository.save(testAppClient);
        val appClient2Id = UUID.randomUUID();
        val testAppClient2 = AppClientUser.builder()
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
}
