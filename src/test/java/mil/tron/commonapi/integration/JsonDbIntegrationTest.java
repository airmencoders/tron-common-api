package mil.tron.commonapi.integration;


import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import mil.tron.commonapi.dto.PrivilegeDto;
import mil.tron.commonapi.dto.ScratchStorageAppUserPrivDto;
import mil.tron.commonapi.dto.ScratchStorageEntryDto;
import mil.tron.commonapi.dto.ScratchStorageUserDto;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest(properties = { "security.enabled=true" })
@TestPropertySource(locations = "classpath:application-test.properties")
@ActiveProfiles(value = { "development", "test" })  // enable at least dev so we get tracing enabled for full integration
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
public class JsonDbIntegrationTest {

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

    private static final String APP_NAME = "DbApp";
    private static final String ENDPOINT = "/v2/scratch/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private ModelMapper mapper = new ModelMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PrivilegeRepository privRepo;

    @Autowired
    private DashboardUserRepository dashRepo;

    String usersContent = "[ " +
            "{ \"id\": \"97031086-58a2-4228-8fa6-6d6544c1102d\", \"age\": 40, \"name\": \"Frank\", \"email\": \"f@test.com\" }, " +
            "{ \"id\": \"97031086-58a2-4228-8fa6-6d6544c1102e\", \"age\": 41, \"name\": \"Bill\", \"email\": \"b@test.com\" } " +
            "]";
    String userSchema = "{ \"id\": \"uuid\", \"age\": \"number\", \"name\": \"string\", \"email\": \"email!*\" }";

    String rolesContent = "[]";
    String rolesSchema = "{ \"id\": \"uuid\", \"roleName\": \"string!*\" }";

    UUID appId = UUID.randomUUID();
    String usersTableName = "users";
    String schemaName = "users_schema";
    String rolesTableName = "roles";
    String rolesSchemaName = "roles_schema";

    // predefine a users table
    private ScratchStorageEntryDto usersTable = ScratchStorageEntryDto
            .builder()
            .appId(appId)
            .key(usersTableName)
            .value(usersContent)
            .build();

    // predefine a users schema
    private ScratchStorageEntryDto usersTableSchema = ScratchStorageEntryDto
            .builder()
            .appId(appId)
            .key(schemaName)
            .value(userSchema)
            .build();

    // predefine a roles table
    private ScratchStorageEntryDto rolesTable = ScratchStorageEntryDto
            .builder()
            .appId(appId)
            .key(rolesTableName)
            .value(rolesContent)
            .build();

    // predefine a roles schema
    private ScratchStorageEntryDto rolesTableSchema = ScratchStorageEntryDto
            .builder()
            .appId(appId)
            .key(rolesSchemaName)
            .value(rolesSchema)
            .build();

    // predefine user1
    private ScratchStorageUserDto user1 = ScratchStorageUserDto
            .builder()
            .email("user1@test.com")
            .build();

    private DashboardUser admin;
    private List<PrivilegeDto> privs;
    private Long scratchAdminPrivId;

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

    /**
     * Setup tasks for the integration test - we masquerade as a user from the SSO with DASHBOARD_ADMIN authority
     * so we can setup the scratch space and the privs to it for the two apps
     * @throws Exception
     */
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

        // persist/create the scratch space user1
        mockMvc.perform(post(ENDPOINT + "users")
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(user1)))
                .andExpect(status().isCreated());

        // get the privs from the db
        privs = Lists.newArrayList(privRepo
                .findAll())
                .stream()
                .map(item -> mapper.map(item, PrivilegeDto.class))
                .collect(Collectors.toList());

        scratchAdminPrivId = privs.stream()
                .filter(item -> item.getName().equals("SCRATCH_ADMIN"))
                .collect(Collectors.toList()).get(0).getId();


        // make the APP_NAME app
        Map<String, String> coolAppRegistration = new HashMap<>();
        coolAppRegistration.put("id", appId.toString());
        coolAppRegistration.put("appName", APP_NAME);
        mockMvc.perform(post(ENDPOINT + "apps")
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER
                        .writeValueAsString(coolAppRegistration)))
                .andExpect(status().isCreated());

        // define user1's privilege set to APP_NAME as SCRATCH_ADMIN
        //  these will be PATCH to the endpoint in a ScratchStorageAppUserPrivDto
        ScratchStorageAppUserPrivDto user1PrivDto =
                ScratchStorageAppUserPrivDto.builder()
                        .email(user1.getEmail())
                        .privilegeId(scratchAdminPrivId)
                        .build();

        mockMvc.perform(patch(ENDPOINT + "apps/{appId}/user", appId)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(user1PrivDto)))
                .andExpect(status().isOk());

        // persist the users table and schema
        mockMvc.perform(post(ENDPOINT)
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(usersTable)))
                .andExpect(status().isOk());
        mockMvc.perform(post(ENDPOINT)
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(usersTableSchema)))
                .andExpect(status().isOk());
        mockMvc.perform(post(ENDPOINT)
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(rolesTable)))
                .andExpect(status().isOk());
        mockMvc.perform(post(ENDPOINT)
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(rolesTableSchema)))
                .andExpect(status().isOk());
    }

    @Transactional
    @Rollback
    @Test
    void testGetById() throws Exception {

        mockMvc.perform(get(ENDPOINT + "{appId}/jsondb/{table}/get/{id}", appId, usersTableName, "97031086-58a2-4228-8fa6-6d6544c1102d")
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", equalTo("Frank")));

        mockMvc.perform(get(ENDPOINT + "{appId}/jsondb/{table}/get/{id}", appId, usersTableName, "97031086-58a2-4228-8fa6-6d6544c1102a")
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(ENDPOINT + "{appId}/jsondb/{table}/get/{id}", appId, rolesTableName, "97031086-58a2-4228-8fa6-6d6544c1102a")
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isNotFound());

    }

    @Transactional
    @Rollback
    @Test
    void testList() throws Exception {

        mockMvc.perform(get(ENDPOINT + "{appId}/jsondb/{table}/list", appId, usersTableName)
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // return empty set
        mockMvc.perform(get(ENDPOINT + "{appId}/jsondb/{table}/list", appId, rolesTableName)
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get(ENDPOINT + "{appId}/jsondb/{table}/list", appId, "random-table")
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isNotFound());

    }

    @Transactional
    @Rollback
    @Test
    void testCreate() throws Exception {
        mockMvc.perform(post(ENDPOINT + "{appId}/jsondb/{table}/create", appId, usersTableName)
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"age\": 67, \"name\": \"Olaf\", \"email\": \"o@test.com\" }"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.age", equalTo(67)))
                .andExpect(jsonPath("$.name", equalTo("Olaf")))
                .andExpect(jsonPath("$.email", equalTo("o@test.com")))
                .andExpect(jsonPath("$.id", notNullValue()));

        // try to add a field marked as unique (in this case email)
        mockMvc.perform(post(ENDPOINT + "{appId}/jsondb/{table}/create", appId, usersTableName)
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"age\": 65, \"name\": \"James\", \"email\": \"o@test.com\" }"))
                .andExpect(status().isConflict());

        // try malformed data, (missing a required "email" field from the schema)
        mockMvc.perform(post(ENDPOINT + "{appId}/jsondb/{table}/create", appId, usersTableName)
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ }"))
                .andExpect(status().isBadRequest());
    }

    @Transactional
    @Rollback
    @Test
    void testUpdate() throws Exception {

        // leaving out the ID field is a failure
        mockMvc.perform(patch(ENDPOINT + "{appId}/jsondb/{table}/update/{id}", appId, usersTableName, "97031086-58a2-4228-8fa6-6d6544c1102d")
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"age\": 65, \"name\": \"James\", \"email\": \"o@test.com\" }"))
                .andExpect(status().isBadRequest());

        // IDs mismatch is a failure
        mockMvc.perform(patch(ENDPOINT + "{appId}/jsondb/{table}/update/{id}", appId, usersTableName, "97031086-58a2-4228-8fa6-6d6544c1102a")
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"id\": \"97031086-58a2-4228-8fa6-6d6544c1102e\", \"age\": 65, \"name\": \"James\", \"email\": \"o@test.com\" }"))
                .andExpect(status().isBadRequest());

        // update succeeds
        mockMvc.perform(patch(ENDPOINT + "{appId}/jsondb/{table}/update/{id}", appId, usersTableName, "97031086-58a2-4228-8fa6-6d6544c1102e")
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"id\": \"97031086-58a2-4228-8fa6-6d6544c1102e\", \"age\": 65, \"name\": \"James\", \"email\": \"o@test.com\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.age", equalTo(65)))
                .andExpect(jsonPath("$.name", equalTo("James")))
                .andExpect(jsonPath("$.email", equalTo("o@test.com")));
    }

    @Transactional
    @Rollback
    @Test
    void testDelete() throws Exception {
        MvcResult result = mockMvc.perform(post(ENDPOINT + "{appId}/jsondb/{table}/create", appId, usersTableName)
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"age\": 67, \"name\": \"Olaf\", \"email\": \"o@test.com\" }"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.age", equalTo(67)))
                .andExpect(jsonPath("$.name", equalTo("Olaf")))
                .andExpect(jsonPath("$.email", equalTo("o@test.com")))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andReturn();

        String id = OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), JsonNode.class).get("id").textValue();

        mockMvc.perform(get(ENDPOINT + "{appId}/jsondb/{table}/list", appId, usersTableName)
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        mockMvc.perform(delete(ENDPOINT + "{appId}/jsondb/{table}/delete/{id}", appId, usersTableName, id)
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isNoContent());

        // test successful delete
        mockMvc.perform(get(ENDPOINT + "{appId}/jsondb/{table}/list", appId, usersTableName)
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // do invalid/non existent ID
        mockMvc.perform(delete(ENDPOINT + "{appId}/jsondb/{table}/delete/{id}", appId, usersTableName, UUID.randomUUID())
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    @Rollback
    void testQuery() throws Exception {

        // doing a query requires a QueryDto object
        mockMvc.perform(post(ENDPOINT + "{appId}/jsondb/{table}/query", appId, usersTableName)
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"query\": \"$[?(@.email == 'b@test.com')]\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // doing a query requires a QueryDto object
        mockMvc.perform(post(ENDPOINT + "{appId}/jsondb/{table}/query", appId, usersTableName)
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"query\": \"$[?(@.age <= 42)]\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }
}
