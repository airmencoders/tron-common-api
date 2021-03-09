package mil.tron.commonapi.integration;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import lombok.val;
import mil.tron.commonapi.CommonApiApplication;
import mil.tron.commonapi.dto.AppClientUserPrivDto;
import mil.tron.commonapi.dto.ScratchStorageAppUserPrivDto;
import mil.tron.commonapi.dto.appsource.AppSourceDetailsDto;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.scratch.ScratchStorageEntry;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import mil.tron.commonapi.repository.appsource.AppSourcePrivRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.transaction.Transactional;
import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("development")
@SpringBootTest(properties = { "security.enabled=true" })
@AutoConfigureMockMvc
public class AppSourceIntegrtionTest {

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
    AppSourcePrivRepository appSourcePrivRepository;

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
    void testCreatAppSource() {
        val appClientUserUuid = UUID.randomUUID();
        appClientUserRespository.save(
                AppClientUser.builder()
                        .id(appClientUserUuid)
                        .name("App User 1")
                        .build()
        );
        List<AppClientUserPrivDto> privDtos = new ArrayList<>();
        privDtos.add(
                AppClientUserPrivDto
                        .builder()
                        .appClientUser(appClientUserUuid)
                        .privileges(Arrays.asList(
                                Privilege.builder()
                                        .id(1L).build()
                        ))
                        .build()
        );
        AppSourceDetailsDto appSource = AppSourceDetailsDto.builder()
                .name("Name")
                .appClients(privDtos)
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
                        .privileges(Arrays.asList(Privilege.builder()
                                .id(0L)
                                .build()))
                        .build()))
                .build();

        mockMvc.perform(post(ENDPOINT)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(appSource)))
                .andExpect(status().is4xxClientError());
    }

    // test bad app client id

    // test with app client and permission


}
