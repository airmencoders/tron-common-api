package mil.tron.commonapi.integration;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.HttpLogEntry;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.HttpLogsRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import javax.transaction.Transactional;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest(properties = { "security.enabled=true" })
@ActiveProfiles(value = { "development", "test" })
@AutoConfigureMockMvc
public class HttpTraceIntegrationTest {

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

    private static final String ENDPOINT = "/v1/logs";
    private static final String PERSON_ENDPOINT = "/v1/person/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HttpLogsRepository httpLogsRepository;

    @Autowired
    private DashboardUserRepository dashRepo;

    @Autowired
    private PrivilegeRepository privRepo;

    private DashboardUser admin;
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

    @BeforeEach
    void setup() {
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
    void testNewTraceIsAdded() throws Exception {

        // get number of traces in there now
        int size = httpLogsRepository.findAll().size();

        PersonDto joe = PersonDto.builder()
                .email("joe@test.com")
                .firstName("Joe")
                .lastName("Public")
                .branch(Branch.USAF)
                .rank("Capt")
                .build();

        mockMvc.perform(post(PERSON_ENDPOINT)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(joe)))
                .andExpect(status().isCreated());


        // make sure the trace is logged
        assertEquals(size+1, httpLogsRepository.findAll().size());

        // get that trace and check a few of the relevant fields
        HttpLogEntry entry = httpLogsRepository.findAll().get(size);
        assertEquals(admin.getEmail(), entry.getUserName());  // the entity that made the request
        assertEquals(201, entry.getStatusCode());  // the result of the operation
        assertEquals("Unknown", entry.getUserAgent());  // no user agent
        assertEquals("POST", entry.getRequestMethod()); // POST for method
        assertEquals(OBJECT_MAPPER.writeValueAsString(joe), entry.getRequestBody()); // we logged the request body
        assertDoesNotThrow(() -> OBJECT_MAPPER.readValue(entry.getResponseBody(), PersonDto.class));  // test we can deserialize the logged response
        assertThat(entry.getRequestedUrl(), containsString("/person"));

        UUID userId = OBJECT_MAPPER.readValue(entry.getResponseBody(), PersonDto.class).getId();

        // do PATCH
        joe.setRank("Maj");
        JSONObject content = new JSONObject();
        content.put("op","replace");
        content.put("path","/rank");
        content.put("value", "Maj");
        JSONArray contentArray = new JSONArray();
        contentArray.put(content);
        mockMvc.perform(patch(PERSON_ENDPOINT + "{id}", userId)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .header("User-Agent", "Mockito")
                .contentType("application/json-patch+json")
                .content(contentArray.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank", equalTo("Maj")));

        HttpLogEntry entry2 = httpLogsRepository.findAll().get(size+1);
        assertEquals(admin.getEmail(), entry2.getUserName());  // the entity that made the request
        assertEquals(200, entry2.getStatusCode());  // the result of the operation
        assertEquals("Mockito", entry2.getUserAgent());  // user agent
        assertEquals("PATCH", entry2.getRequestMethod()); // PATCH for method
        assertDoesNotThrow(() -> OBJECT_MAPPER.readValue(entry2.getResponseBody(), PersonDto.class));  // test we can deserialize the logged response
        assertThat(entry2.getRequestedUrl(), containsString("/person"));

        // do a re-POST to check we log a 409
        mockMvc.perform(post(PERSON_ENDPOINT)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(joe)))
                .andExpect(status().isConflict());

        assertEquals(size+3, httpLogsRepository.findAll().size());
        HttpLogEntry entry3 = httpLogsRepository.findAll().get(size+2);
        assertEquals(409, entry3.getStatusCode());

        // do a malformed to check we log a 400
        mockMvc.perform(post(PERSON_ENDPOINT)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"name\": \"bogus\""))
                .andExpect(status().isBadRequest());

        assertEquals(size+4, httpLogsRepository.findAll().size());
        HttpLogEntry entry4 = httpLogsRepository.findAll().get(size+3);
        assertEquals(400, entry4.getStatusCode());

        mockMvc.perform(post(PERSON_ENDPOINT)
                .header(AUTH_HEADER_NAME, createToken("some@dude.com"))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(joe)))
                .andExpect(status().isForbidden());

        assertEquals(size+5, httpLogsRepository.findAll().size());
        HttpLogEntry entry5 = httpLogsRepository.findAll().get(size+4);
        assertEquals(403, entry5.getStatusCode());
        assertTrue(entry5.getResponseBody().contains("denied"));
    }
}
