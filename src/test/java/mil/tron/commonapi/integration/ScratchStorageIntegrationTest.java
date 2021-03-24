package mil.tron.commonapi.integration;


import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import mil.tron.commonapi.dto.ScratchStorageAppRegistryDto;
import mil.tron.commonapi.dto.ScratchStorageAppUserPrivDto;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.scratch.ScratchStorageAppRegistryEntry;
import mil.tron.commonapi.entity.scratch.ScratchStorageAppUserPriv;
import mil.tron.commonapi.entity.scratch.ScratchStorageEntry;
import mil.tron.commonapi.entity.scratch.ScratchStorageUser;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * Full stack test from controller to H2 for scratch space.
 *
 * This test also manually enables 'security.enabled=true'
 * so that the security filters and authentication framework
 * is in place.
 *
 * The setup is:
 *      + Two (2) mock applications named "CoolApp" and "TestApp"
 *      + Two (2) users
 *          - "USER1" who has WRITE privs to CoolApp
 *          - "USER2" who has WRITE privs to TestApp
 *
 * Setup is done as a "DASHBOARD_ADMIN" user in the setup() method.
 */

@ExtendWith(SpringExtension.class)
@SpringBootTest(properties = { "security.enabled=true" })
@AutoConfigureMockMvc
public class ScratchStorageIntegrationTest {

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

    private static final String COOL_APP_NAME = "CoolApp";
    private static final String TEST_APP_NAME = "TestApp";
    private static final String ENDPOINT = "/v1/scratch/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PrivilegeRepository privRepo;

    @Autowired
    private DashboardUserRepository dashRepo;

    // predefine a key value pair for COOL_APP_NAME
    private ScratchStorageEntry entry1 = ScratchStorageEntry
            .builder()
            .appId(UUID.randomUUID())
            .key("hello")
            .value("world")
            .build();

    // predefine a key value pair for TEST_APP_NAME
    private ScratchStorageEntry entry2 = ScratchStorageEntry
            .builder()
            .appId(UUID.randomUUID())
            .key("some key")
            .value("value")
            .build();

    // predefine user1
    private ScratchStorageUser user1 = ScratchStorageUser
            .builder()
            .email("user1@test.com")
            .build();

    // predefine user2
    private ScratchStorageUser user2 = ScratchStorageUser
            .builder()
            .email("user2@test.com")
            .build();


    private DashboardUser admin;
    private List<Privilege> privs;
    private Long writePrivId;

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

        // persist/create the scratch space users - user1 and user2
        mockMvc.perform(post(ENDPOINT + "users")
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(user1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(ENDPOINT + "users")
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(user2)))
                .andExpect(status().isCreated());


        // get the privs from the db
        privs = Lists.newArrayList(privRepo.findAll());
        writePrivId = privs.stream()
                .filter(item -> item.getName().equals("SCRATCH_WRITE"))
                .collect(Collectors.toList()).get(0).getId();


        // predefine a key value pair for TEST_APP_NAME
        ScratchStorageEntry entry3 = ScratchStorageEntry
                .builder()
                .appId(entry2.getAppId())
                .key("some key2")
                .value("value")
                .build();


        // register the appIds of the above key value pairs into
        //  two "apps" under COOL_APP_NAME and TEST_APP_NAME
        Map<String, String> coolAppRegistration = new HashMap<>();
        coolAppRegistration.put("id", entry1.getAppId().toString());
        coolAppRegistration.put("appName", COOL_APP_NAME);
        mockMvc.perform(post(ENDPOINT + "apps")
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER
                        .writeValueAsString(coolAppRegistration)))
                .andExpect(status().isCreated());

        // define user1's privilege set to COOL_APP
        //  these will be PATCH to the endpoint in a ScratchStorageAppUserPrivDto
        ScratchStorageAppUserPrivDto user1PrivDto =
                ScratchStorageAppUserPrivDto.builder()
                .email(user1.getEmail())
                .privilegeId(writePrivId)
                .build();

        mockMvc.perform(patch(ENDPOINT + "apps/{appId}/user", entry1.getAppId())
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(user1PrivDto)))
                .andExpect(status().isOk());

        Map<String, String> testAppRegistration = new HashMap<>();
        testAppRegistration.put("id", entry2.getAppId().toString());
        testAppRegistration.put("appName", TEST_APP_NAME);
        mockMvc.perform(post(ENDPOINT + "apps")
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER
                        .writeValueAsString(testAppRegistration)))
                .andExpect(status().isCreated());

        // define user2's privilege set to TEST_APP
        //  these will be PATCH to the endpoint in a ScratchStorageAppUserPrivDto
        ScratchStorageAppUserPrivDto user2PrivDto =
                ScratchStorageAppUserPrivDto.builder()
                        .email(user2.getEmail())
                        .privilegeId(writePrivId)
                        .build();

        mockMvc.perform(patch(ENDPOINT + "apps/{appId}/user", entry2.getAppId())
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(user2PrivDto)))
                .andExpect(status().isOk());

        // go ahead and attach/persist the key-value entries for TEST_APP_NAME
        mockMvc.perform(post(ENDPOINT)
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .header(AUTH_HEADER_NAME, createToken(user2.getEmail()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(entry2)))
                .andExpect(status().isOk());
        mockMvc.perform(post(ENDPOINT)
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .header(AUTH_HEADER_NAME, createToken(user2.getEmail()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(entry3)))
                .andExpect(status().isOk());


    }

    @Transactional
    @Rollback
    @Test
    void testInvalidAppID() throws Exception {

        // test null as the appid will never work

        ScratchStorageEntry entry = ScratchStorageEntry
                .builder()
                .appId(null)
                .key("some key2")
                .value("value")
                .build();


        mockMvc.perform(post(ENDPOINT)
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(entry)))
                .andExpect(status().isBadRequest());
    }

    @Transactional
    @Rollback
    @Test
    void testInvalidKeyValue() throws Exception {

        // test notnull constraint on keys won't work

        ScratchStorageEntry entry = ScratchStorageEntry
                .builder()
                .appId(UUID.randomUUID())
                .key(null)
                .value("value")
                .build();

        // null key not allowed
        mockMvc.perform(post(ENDPOINT)
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(entry)))
                .andExpect(status().isBadRequest());

        entry.setKey("");

        // blank key not allowed
        mockMvc.perform(post(ENDPOINT)
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(entry)))
                .andExpect(status().isBadRequest());

    }

    @Transactional
    @Rollback
    @Test
    void testAddKeyValuePair() throws Exception {

        // test we can add a key value pair to COOL_APP_NAME

        mockMvc.perform(post(ENDPOINT)
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(entry1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId", equalTo(entry1.getAppId().toString())));
    }

    @Transactional
    @Rollback
    @Test
    void getAllKeyValuePairs() throws Exception {

        // have to be a dashboard admin to do this
        mockMvc.perform(get(ENDPOINT)
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Transactional
    @Rollback
    @Test
    void getAllKeyValuePairsByAppId() throws Exception {
        mockMvc.perform(get(ENDPOINT + "{appId}", entry2.getAppId())
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .header(AUTH_HEADER_NAME, createToken(user2.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Transactional
    @Rollback
    @Test
    void getKeyValuePairByAppIdAndKeyName() throws Exception {
        mockMvc.perform(get(ENDPOINT + "{appId}/{keyName}", entry2.getAppId(), entry2.getKey())
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .header(AUTH_HEADER_NAME, createToken(user2.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value", equalTo(entry2.getValue())));

        // test key doesnt exist
        mockMvc.perform(get(ENDPOINT + "{appId}/{keyName}", entry2.getAppId(), "bogus key")
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .header(AUTH_HEADER_NAME, createToken(user2.getEmail())))
                .andExpect(status().isNotFound());
    }

    @Transactional
    @Rollback
    @Test
    void getAllKeysForApp() throws Exception {
        mockMvc.perform(get(ENDPOINT + "/apps/{appId}/keys", entry2.getAppId())
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .header(AUTH_HEADER_NAME, createToken(user2.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]", equalTo(entry2.getKey())));
    }

    @Transactional
    @Rollback
    @Test
    void deleteKeyValuePairsByAppId() throws Exception {

        // add key value pair to COOL_APP, should have 3 TOTAL key value pairs
        //  1 for COOL_APP and 2 for TEST_APP
        mockMvc.perform(post(ENDPOINT)
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(entry1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId", equalTo(entry1.getAppId().toString())));

        // delete all of TEST_APPs key value pairs
        mockMvc.perform(delete(ENDPOINT + "{appId}", entry2.getAppId())
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .header(AUTH_HEADER_NAME, createToken(user2.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // should be no key value pairs for TEST_APP
        mockMvc.perform(get(ENDPOINT + "{appId}", entry2.getAppId())
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .header(AUTH_HEADER_NAME, createToken(user2.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Transactional
    @Rollback
    @Test
    void deleteKeyValuePair() throws Exception {

        // add key value pair to COOL_APP, should have 3 TOTAL key value pairs
        //  1 for COOL_APP and 2 for TEST_APP
        mockMvc.perform(post(ENDPOINT)
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(entry1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId", equalTo(entry1.getAppId().toString())));

        // delete just one key value pairs from TEST_APP
        mockMvc.perform(delete(ENDPOINT + "{appId}/{keyValue}", entry2.getAppId(), entry2.getKey())
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .header(AUTH_HEADER_NAME, createToken(user2.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value", equalTo(entry2.getValue())));

        // TEST_APP should have only 1 key value pair left
        mockMvc.perform(get(ENDPOINT + "{appId}", entry2.getAppId())
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .header(AUTH_HEADER_NAME, createToken(user2.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // delete COOL_APP's key as user2 - should be forbidden
        mockMvc.perform(delete(ENDPOINT + "{appId}/{keyValue}", entry1.getAppId(), "hello")
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .header(AUTH_HEADER_NAME, createToken(user2.getEmail())))
                .andExpect(status().isForbidden());

        // delete bogus key
        mockMvc.perform(delete(ENDPOINT + "{appId}/{keyValue}", entry2.getAppId(), "bogus key")
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .header(AUTH_HEADER_NAME, createToken(user2.getEmail())))
                .andExpect(status().isNotFound());
    }


    @Transactional
    @Rollback
    @Test
    void testNonExistentAppLookup() throws Exception {

        // should get 404 for a UUID of a non-existent (non-registered) app

        mockMvc.perform(get(ENDPOINT + "{appId}/{keyName}", UUID.randomUUID(), "some key")
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail())))
                .andExpect(status().isNotFound());
    }

    /**
     * Full stack test from an admin blessing 'user1' to be a SCRATCH_ADMIN and then 'user1' adding a new user
     * to his app (COOL_APP) and then making that new user have WRITE access, revoking WRITE & giving READ access, and
     * then finally revoking their app access completely, and making sure the new guy is still in the scratch space
     * universe though... and verifying all of this along the way.
     * @throws Exception if Jackson croaks
     */
    @Transactional
    @Rollback
    @Test
    void testScratchAdminFunctions() throws Exception {

        // emulate a user1 (who has been upgraded as a SCRATCH_ADMIN) performing
        //   admin functions on assigned App space

        String newGuyEmail = "newguy@test.com";  // new scratch user we'll add later

        // first check that user1 with SCRATCH_WRITE can't get COOL_APP's record
        mockMvc.perform(get(ENDPOINT + "apps/{appId}", entry1.getAppId())
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isForbidden());


        // get the priv ID of SCRATCH_ADMIN from the REST interface
        MvcResult privList = mockMvc.perform(get("/v1/scratch/users/privs")
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andReturn();
        Privilege[] privArray = OBJECT_MAPPER
                .readValue(privList.getResponse().getContentAsString(), Privilege[].class);

        // yank out the SCRATCH_ADMIN from the JSON
        Privilege adminPriv = Arrays
                .stream(privArray)
                .filter(item -> item.getName().equals("SCRATCH_ADMIN"))
                .collect(Collectors.toList()).get(0);

        // yank out the SCRATCH_WRITE details from the JSON
        Privilege writePriv = Arrays
                .stream(privArray)
                .filter(item -> item.getName().equals("SCRATCH_WRITE"))
                .collect(Collectors.toList()).get(0);

        // yank out the SCRATCH_READ details from the JSON
        Privilege readPriv = Arrays
                .stream(privArray)
                .filter(item -> item.getName().equals("SCRATCH_READ"))
                .collect(Collectors.toList()).get(0);

        // bless user1 with scratch admin rights to his app space
        ScratchStorageAppUserPrivDto user1PrivDto =
                ScratchStorageAppUserPrivDto.builder()
                        .email(user1.getEmail())
                        .privilegeId(adminPriv.getId())
                        .build();

        // as the dashboard admin, get COOL_APP's record - which include its privs
        MvcResult result = mockMvc.perform(get(ENDPOINT + "apps/{appId}", entry1.getAppId())
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andReturn();

        ScratchStorageAppRegistryDto appDto = OBJECT_MAPPER.readValue(
                result.getResponse().getContentAsString(), ScratchStorageAppRegistryDto.class);

        // get user1's priv entry in the returned Dto, get its ID
        ScratchStorageAppUserPriv user1AdminPriv = null;
        for (ScratchStorageAppRegistryDto.UserWithPrivs user : appDto.getUserPrivs()) {
            if (user.getUserId().equals(user1.getId())) {
                for (ScratchStorageAppRegistryDto.PrivilegeIdPair pair : user.getPrivs()) {
                    if (pair.getPriv().getName().equals("SCRATCH_WRITE")) {
                        user1PrivDto.setId(pair.getUserPrivPairId());
                    }
                }
            }
        }

        // as the dashboard admin, PATCH the existing priv for user1, now user1 should be an ADMIN for his space
        mockMvc.perform(patch(ENDPOINT + "apps/{appId}/user", entry1.getAppId())
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(user1PrivDto)))
                .andExpect(status().isOk());

        // now verify that user1 can get his app's record and all the attached users, doesn't take a DashboardAdmin anymore
        mockMvc.perform(get(ENDPOINT + "apps/{appId}", entry1.getAppId())
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk());


        // now test that user1, can add a new user to their app, a user that isn't even in the scratch storage
        //   universe yet, new user will be implictly added/assigned a UUID/ and placed against this app with chosen priv
        ScratchStorageAppUserPrivDto newUserDto = ScratchStorageAppUserPrivDto
                .builder()
                .email(newGuyEmail)
                .privilegeId(writePriv.getId())
                .build();

        // make sure 'user2' can't do this
        mockMvc.perform(patch(ENDPOINT + "apps/{appId}/user", entry1.getAppId())
                .header(AUTH_HEADER_NAME, createToken(user2.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(newUserDto)))
                .andExpect(status().isForbidden());

        // user1 should definitely be able to
        mockMvc.perform(patch(ENDPOINT + "apps/{appId}/user", entry1.getAppId())
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(newUserDto)))
                .andExpect(status().isOk());


        // make sure that the new guy we just added is there and has SCRATCH_WRITE privs
        MvcResult newDetails = mockMvc.perform(get(ENDPOINT + "apps/{appId}", entry1.getAppId())
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andReturn();

        ScratchStorageAppRegistryDto newDetailsDto = OBJECT_MAPPER.readValue(
                newDetails.getResponse().getContentAsString(), ScratchStorageAppRegistryDto.class);

        // sift thru the COOL_APP's record and make sure the new guy is listed (created) with SCRATCH_WRITE privs
        boolean userHasWritePriv = false;
        for (ScratchStorageAppRegistryDto.UserWithPrivs user : newDetailsDto.getUserPrivs()) {
            if (user.getEmailAddress().equals(newGuyEmail)) {
                for (ScratchStorageAppRegistryDto.PrivilegeIdPair priv : user.getPrivs()) {
                    if (priv.getPriv().getName().equals("SCRATCH_WRITE")) {
                        userHasWritePriv = true;
                        break;
                    }
                }
            }
            if (userHasWritePriv) break;
        }

        assertTrue(userHasWritePriv);


        // now take away the new guy's WRITE access and make READ
        newUserDto.setPrivilegeId(readPriv.getId());
        mockMvc.perform(patch(ENDPOINT + "apps/{appId}/user", entry1.getAppId())
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(newUserDto)))
                .andExpect(status().isOk());

        // make sure that the new guy we just added is there and has SCRATCH_READ privs
        MvcResult readOnlyDetails = mockMvc.perform(get(ENDPOINT + "apps/{appId}", entry1.getAppId())
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andReturn();

        ScratchStorageAppRegistryDto readOnlyDetailsDto = OBJECT_MAPPER.readValue(
                readOnlyDetails.getResponse().getContentAsString(), ScratchStorageAppRegistryDto.class);

        // sift thru the COOL_APP's record and make sure the new guy is listed (created) with SCRATCH_READ privs
        boolean userHasReadPriv = false;
        UUID privEntryId = null;
        for (ScratchStorageAppRegistryDto.UserWithPrivs user : readOnlyDetailsDto.getUserPrivs()) {
            if (user.getEmailAddress().equals(newGuyEmail)) {
                for (ScratchStorageAppRegistryDto.PrivilegeIdPair priv : user.getPrivs()) {
                    if (priv.getPriv().getName().equals("SCRATCH_READ")) {
                        privEntryId = priv.getUserPrivPairId();
                        userHasReadPriv = true;
                        break;
                    }
                }
            }
            if (userHasReadPriv) break;
        }

        assertTrue(userHasReadPriv);

        // make sure the READ priv works - get the app's key names
        mockMvc.perform(get(ENDPOINT + "/apps/{appId}/keys", entry1.getAppId())
                .header(AUTH_HEADER_NAME, createToken(newUserDto.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk());

        // finally revoke the new guys privs - but the system will not delete that user from the
        //   scratch space universe, since they may be referenced for other apps!
        // we can use the privEntryId retrieved above in the last app details pull, we delete using that identifier to
        //  single out the priv we want gone
        mockMvc.perform(delete(ENDPOINT + "apps/{appId}/user/{privId}", entry1.getAppId(), privEntryId)
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk());

        // verify that the new user is no longer associated with COOL_APP
        MvcResult finalDetails = mockMvc.perform(get(ENDPOINT + "apps/{appId}", entry1.getAppId())
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andReturn();

        ScratchStorageAppRegistryDto finalDetailsDto = OBJECT_MAPPER.readValue(
                finalDetails.getResponse().getContentAsString(), ScratchStorageAppRegistryDto.class);

        // sift thru the COOL_APP's record and make sure the new guy is listed (created) with SCRATCH_READ privs
        boolean userIsPresent = false;
        for (ScratchStorageAppRegistryDto.UserWithPrivs user : finalDetailsDto.getUserPrivs()) {
            if (user.getEmailAddress().equals(newGuyEmail)) {
                userIsPresent = true;
                break;
            }
            if (userIsPresent) break;
        }

        assertFalse(userIsPresent);

        // verify the new user is still in the system though, gotta use our Dashboard_Admin creds for this request
        MvcResult allUsers = mockMvc.perform(get(ENDPOINT + "users")
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andReturn();

        ScratchStorageUser[] allUsersArray = OBJECT_MAPPER.readValue(allUsers.getResponse().getContentAsString(),
                ScratchStorageUser[].class);

        // make sure the new guy is still in scratch space universe
        boolean newGuyExists = false;
        for (ScratchStorageUser u : allUsersArray) {
            if (u.getEmail().equals(newGuyEmail)) {
                newGuyExists = true;
                break;
            }
            if (newGuyExists) break;
        }

        assertTrue(newGuyExists);

        // make sure the new guy cannot read from app anymore since no privs for it
        mockMvc.perform(get(ENDPOINT + "/apps/{appId}/keys", entry1.getAppId())
                .header(AUTH_HEADER_NAME, createToken(newUserDto.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isForbidden());

        // as admin turn ON implicit read
        mockMvc.perform(patch(ENDPOINT + "/apps/{appId}/implicitRead?value=true", entry1.getAppId())
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk());

        // make sure the new guy can read again -- even without the READ privs
        mockMvc.perform(get(ENDPOINT + "/apps/{appId}/keys", entry1.getAppId())
                .header(AUTH_HEADER_NAME, createToken(newUserDto.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk());

        // as admin turn OFF implicit read
        mockMvc.perform(patch(ENDPOINT + "/apps/{appId}/implicitRead?value=false", entry1.getAppId())
                .header(AUTH_HEADER_NAME, createToken(user1.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk());

        // read from new guy should fail again
        mockMvc.perform(get(ENDPOINT + "/apps/{appId}/keys", entry1.getAppId())
                .header(AUTH_HEADER_NAME, createToken(newUserDto.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isForbidden());
    }

    @Transactional
    @Rollback
    @Test
    void testRemoveUserFromScratchSpaceEverywhere() throws Exception {


        // add user2 to COOL_APP - so both user1 and user2 shall be authorized on that app
        ScratchStorageAppUserPrivDto user2PrivDto =
                ScratchStorageAppUserPrivDto.builder()
                        .email(user2.getEmail())
                        .privilegeId(writePrivId)
                        .build();

        mockMvc.perform(patch(ENDPOINT + "apps/{appId}/user", entry1.getAppId())
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(user2PrivDto)))
                .andExpect(status().isOk());

        // now as the dashboard admin we delete user2 completely from the scratch universe
        mockMvc.perform(delete(ENDPOINT + "users/{userId}", user2.getId())
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk());

        // verify that user2 is no longer listed in COOL_APP or TEST_APP's privs
        MvcResult allAppRecords = mockMvc.perform(get(ENDPOINT + "/apps")
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk())
                .andReturn();

        ScratchStorageAppRegistryDto[] appArray = OBJECT_MAPPER.readValue(allAppRecords.getResponse().getContentAsString(),
                ScratchStorageAppRegistryDto[].class);

        boolean foundUser2Id = false;
        for (ScratchStorageAppRegistryDto entry : appArray) {
            for (ScratchStorageAppRegistryDto.UserWithPrivs priv : entry.getUserPrivs()) {
                if (priv.getUserId().equals(user2.getId())) {
                    foundUser2Id = true;
                    break;
                }
            }
            if (foundUser2Id) break;
        }

        assertFalse(foundUser2Id);
    }

    @Transactional
    @Rollback
    @Test
    void testDeleteAppAndAllItsData() throws Exception {

        // delete TEST_APP should take all of its key-values with it

        // make sure "some key" exists
        mockMvc.perform(get(ENDPOINT + "{appId}/{keyName}", entry2.getAppId(), "some key")
                .header(AUTH_HEADER_NAME, createToken(user2.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk());

        mockMvc.perform(delete(ENDPOINT + "apps/{appId}", entry2.getAppId())
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isOk());

        mockMvc.perform(get(ENDPOINT + "{appId}/{keyName}", entry2.getAppId(), "some key")
                .header(AUTH_HEADER_NAME, createToken(user2.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER))
                .andExpect(status().isNotFound());
    }

    @Transactional
    @Rollback
    @Test
    void testErrorConditions() throws Exception {

        // Note, that "user" doing these tests are done as an admin

        // test various error conditions are caught
            // blank or null email
            // blank or null appName
            // email or appName with leading or trailing whitespace
            // try to add duplicate email with varying cases is denied
            // try to add duplicate app names with varying cases is denied
            // check that user-priv sets per app is not allowed to have duplicates


        // make a new user - "user3"
        ScratchStorageUser user3 = ScratchStorageUser
                .builder()
                .email("")
                .build();

        //  with blank email should fail
        mockMvc.perform(post(ENDPOINT + "users")
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(user3)))
                .andExpect(status().isBadRequest());

        // with null email should fail
        user3.setEmail(null);
        mockMvc.perform(post(ENDPOINT + "users")
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(user3)))
                .andExpect(status().isBadRequest());

        // with duplicate email fails
        user3.setEmail("user1@test.com");
        mockMvc.perform(post(ENDPOINT + "users")
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(user3)))
                .andExpect(status().isConflict());

        // with malformed email fails
        user3.setEmail("user1..test.com");
        mockMvc.perform(post(ENDPOINT + "users")
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(user3)))
                .andExpect(status().isBadRequest());

        // valid email finally succeeds
        user3.setEmail("valid@test.com");
        MvcResult result = mockMvc.perform(post(ENDPOINT + "users")
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(user3)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID user3Id = OBJECT_MAPPER
                .readValue(result.getResponse().getContentAsString(), ScratchStorageUser.class)
                .getId();

        // test edit fails when setting to duplicate email, even with varying case
        user3.setId(user3Id);
        user3.setEmail("user1@TEST.com");
        mockMvc.perform(put(ENDPOINT + "users/{id}", user3Id)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(user3)))
                .andExpect(status().isConflict());

        // test edit fails when setting to duplicate email with leading and trailing whitespace still fails
        user3.setId(user3Id);
        user3.setEmail("   user1@test.com      ");
        mockMvc.perform(put(ENDPOINT + "users/{id}", user3Id)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(user3)))
                .andExpect(status().isBadRequest());


        // register new "App3", but with COOL_APP's name - should fail
        Map<String, String> app3Registration = new HashMap<>();
        app3Registration.put("appName", COOL_APP_NAME);
        mockMvc.perform(post(ENDPOINT + "apps")
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER
                        .writeValueAsString(app3Registration)))
                .andExpect(status().isConflict());

        // same app name varying case should still fail
        app3Registration.put("appName", COOL_APP_NAME.toUpperCase());
        mockMvc.perform(post(ENDPOINT + "apps")
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER
                        .writeValueAsString(app3Registration)))
                .andExpect(status().isConflict());

        // same app name with leading and trailing whitespace shall fail
        app3Registration.put("appName", "     " + COOL_APP_NAME + "    ");
        mockMvc.perform(post(ENDPOINT + "apps")
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER
                        .writeValueAsString(app3Registration)))
                .andExpect(status().isConflict());

        // blank app name should fail
        app3Registration.put("appName", "");
        mockMvc.perform(post(ENDPOINT + "apps")
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER
                        .writeValueAsString(app3Registration)))
                .andExpect(status().isBadRequest());

        // null app name should fail
        app3Registration.put("appName", null);
        mockMvc.perform(post(ENDPOINT + "apps")
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER
                        .writeValueAsString(app3Registration)))
                .andExpect(status().isBadRequest());


        // finally let app3 register
        app3Registration.put("appName", "app3");
        MvcResult newApp = mockMvc.perform(post(ENDPOINT + "apps")
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER
                        .writeValueAsString(app3Registration)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID app3Id = OBJECT_MAPPER
                .readValue(newApp.getResponse().getContentAsString(), ScratchStorageAppRegistryEntry.class)
                .getId();

        // test that edit app3 entry with existing app name fails
        app3Registration.put("id", app3Id.toString());
        app3Registration.put("appName", COOL_APP_NAME);
        mockMvc.perform(put(ENDPOINT + "apps/{appId}", app3Id)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER
                        .writeValueAsString(app3Registration)))
                .andExpect(status().isConflict());


        // get the privs from the db
        List<Privilege> privs = Lists.newArrayList(privRepo.findAll());
        Long writePrivId = privs.stream()
                .filter(item -> item.getName().equals("SCRATCH_WRITE"))
                .collect(Collectors.toList()).get(0).getId();

        // define user1's privilege set to COOL_APP
        //  these will be PATCH to the endpoint in a ScratchStorageAppUserPrivDto
        ScratchStorageAppUserPrivDto user3PrivDto =
                ScratchStorageAppUserPrivDto.builder()
                        .email(null)
                        .privilegeId(writePrivId)
                        .build();

        // null email in the priv dto should fail
        mockMvc.perform(patch(ENDPOINT + "apps/{appId}/user", app3Id)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(user3PrivDto)))
                .andExpect(status().isBadRequest());

        // blank email in the priv dto should fail
        user3PrivDto.setEmail("");
        mockMvc.perform(patch(ENDPOINT + "apps/{appId}/user", app3Id)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(user3PrivDto)))
                .andExpect(status().isBadRequest());

        // malformed email in the priv dto should fail
        user3PrivDto.setEmail("blah");
        mockMvc.perform(patch(ENDPOINT + "apps/{appId}/user", app3Id)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(user3PrivDto)))
                .andExpect(status().isBadRequest());

        // email with spaces and in all caps in the priv dto should be OK
        user3PrivDto.setEmail(user3.getEmail().toUpperCase());
        mockMvc.perform(patch(ENDPOINT + "apps/{appId}/user", app3Id)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(user3PrivDto)))
                .andExpect(status().isOk());

        // test that we get CONFLICT on trying to add a priv set that already exists for given app/user/priv combo
        mockMvc.perform(patch(ENDPOINT + "apps/{appId}/user", app3Id)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(user3PrivDto)))
                .andExpect(status().isConflict());
    }
}
