package mil.tron.commonapi.integration;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.dto.OrganizationDto;
import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@ActiveProfiles(value = { "development" })
@TestPropertySource(locations = { "classpath:application.properties", "classpath:application-development.properties" })
@SpringBootTest(properties = { "security.enabled=true" })
@AutoConfigureMockMvc
public class InputFuzzer {

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
    private MockMvc mockMvc;

    DashboardUser admin;

    @Autowired
    PrivilegeRepository privRepo;

    @Autowired
    DashboardUserRepository dashRepo;

    private UUID adminId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        // create the admin
        admin = DashboardUser.builder()
                .id(adminId)
                .email("admin@tron.com")
                .privileges(Set.of(privRepo.findByName("DASHBOARD_ADMIN").orElseThrow(() -> new RecordNotFoundException("No DASH_BOARD ADMIN"))))
                .build();

        // persist the admin
        dashRepo.save(admin);

    }

    @AfterEach
    void cleanup() {
        dashRepo.deleteById(adminId);
    }

    @Nested
    class TestPersonInputs {

        private static final String ENDPOINT = "/v2/person";

        /**
         * The fields for a Person - that are validated - are:
         *  email - must be proper email address with a TLDN
         *  phone number - must be (\d+{3}) \d+{3}-\d+{4}
         *  branch - must be OTHER,USA,USAF,USMC,USN,USSF, or USCG
         *  rank - must be valid for given branch type
         *  dodid - must be \d+{5-10}
         */
        @Test
        void testPersonInputs() throws Exception {

            // person POST only accepts application/json
            mockMvc.perform(post(ENDPOINT)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.TEXT_PLAIN)
                .content("{\n" +
                        "    \"firstName\": \"John\",\n" +
                        "    \"middleName\": \"J\",\n" +
                        "    \"lastName\": \"Smith\",\n" +
                        "    \"email\": \"js@TEST.com\",\n" +
                        "    \"rank\": \"Capt\",\n" +
                        "    \"branch\": \"USAF\"\n" +
                        "}"))
                .andExpect(status().isUnsupportedMediaType());

            // person PUT only accepts application/json
            mockMvc.perform(put(ENDPOINT + "/{id}", UUID.randomUUID())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("{\n" +
                            "    \"firstName\": \"John\",\n" +
                            "    \"middleName\": \"J\",\n" +
                            "    \"lastName\": \"Smith\",\n" +
                            "    \"email\": \"js@TEST.com\",\n" +
                            "    \"rank\": \"Capt\",\n" +
                            "    \"branch\": \"USAF\"\n" +
                            "}"))
                    .andExpect(status().isUnsupportedMediaType());

            // person PATCH only accepts application/json-patch+json
            mockMvc.perform(patch(ENDPOINT + "/{id}", UUID.randomUUID())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("{\n" +
                            "    \"firstName\": \"John\",\n" +
                            "    \"middleName\": \"J\",\n" +
                            "    \"lastName\": \"Smith\",\n" +
                            "    \"email\": \"js@TEST.com\",\n" +
                            "    \"rank\": \"Capt\",\n" +
                            "    \"branch\": \"USAF\"\n" +
                            "}"))
                    .andExpect(status().isUnsupportedMediaType());

            // person post only accepts proper email addresses
            mockMvc.perform(post(ENDPOINT)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\n" +
                            "    \"firstName\": \"John\",\n" +
                            "    \"middleName\": \"J\",\n" +
                            "    \"lastName\": \"Smith\",\n" +
                            "    \"email\": \"js@TEST\",\n" +
                            "    \"rank\": \"Capt\",\n" +
                            "    \"branch\": \"USAF\"\n" +
                            "}"))
                    .andExpect(status().isBadRequest());

            // person post only accepts proper UUID
            mockMvc.perform(post(ENDPOINT)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\n" +
                            "    \"firstName\": \"John\",\n" +
                            "    \"middleName\": \"J\",\n" +
                            "    \"lastName\": \"Smith\",\n" +
                            "    \"email\": \"js@TEST.com\",\n" +
                            "    \"rank\": \"Capt\",\n" +
                            "    \"branch\": \"USAF\"\n" +
                            "    \"primaryOrganizationId\": \"123\"\n" +
                            "}"))
                    .andExpect(status().isBadRequest());

            // person post only accepts proper dodid
            mockMvc.perform(post(ENDPOINT)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\n" +
                            "    \"firstName\": \"John\",\n" +
                            "    \"middleName\": \"J\",\n" +
                            "    \"lastName\": \"Smith\",\n" +
                            "    \"email\": \"js@TEST.com\",\n" +
                            "    \"rank\": \"Capt\",\n" +
                            "    \"dodid\": \"111\",\n" +
                            "    \"branch\": \"USAF\"\n" +
                            "}"))
                    .andExpect(status().isBadRequest());

            // person post only accepts proper phone number
            mockMvc.perform(post(ENDPOINT)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\n" +
                            "    \"firstName\": \"John\",\n" +
                            "    \"middleName\": \"J\",\n" +
                            "    \"lastName\": \"Smith\",\n" +
                            "    \"email\": \"js@TEST.com\",\n" +
                            "    \"rank\": \"Capt\",\n" +
                            "    \"dodid\": \"1234512\",\n" +
                            "    \"phone\": \"1234512\",\n" +
                            "    \"branch\": \"USAF\"\n" +
                            "}"))
                    .andExpect(status().isBadRequest());

            // person post only accepts proper dutyPhone number
            mockMvc.perform(post(ENDPOINT)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\n" +
                            "    \"firstName\": \"John\",\n" +
                            "    \"middleName\": \"J\",\n" +
                            "    \"lastName\": \"Smith\",\n" +
                            "    \"email\": \"js@TEST.com\",\n" +
                            "    \"rank\": \"Capt\",\n" +
                            "    \"dutyPhone\": \"1234512\",\n" +
                            "    \"branch\": \"USAF\"\n" +
                            "}"))
                    .andExpect(status().isBadRequest());

            // person post only accepts proper branch
            mockMvc.perform(post(ENDPOINT)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\n" +
                            "    \"firstName\": \"John\",\n" +
                            "    \"middleName\": \"J\",\n" +
                            "    \"lastName\": \"Smith\",\n" +
                            "    \"email\": \"js@TEST.com\",\n" +
                            "    \"rank\": \"Capt\",\n" +
                            "    \"branch\": \"USPC\"\n" +
                            "}"))
                    .andExpect(status().isBadRequest());

            // test that string fields are limited to 255 chars, throws 500 error
            assertThrows(Exception.class, () ->
                mockMvc.perform(post(ENDPOINT)
                        .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                        .header(XFCC_HEADER_NAME, XFCC_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\n" +
                                "    \"firstName\":\"" + StringUtils.repeat('J', 256) + "\",\n" +
                                "    \"middleName\": \"J\",\n" +
                                "    \"lastName\": \"Smith\",\n" +
                                "    \"email\": \"js@TEST.com\",\n" +
                                "    \"rank\": \"Capt\",\n" +
                                "    \"branch\": \"USAF\"\n" +
                                "}"))
            );

            // test GO Path
            MvcResult result = mockMvc.perform(post(ENDPOINT)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\n" +
                            "    \"firstName\": \"John\",\n" +
                            "    \"middleName\": \"J\",\n" +
                            "    \"lastName\": \"Smith\",\n" +
                            "    \"email\": \"js@TEST.domain.com\",\n" +
                            "    \"rank\": \"Capt\",\n" +
                            "    \"branch\": \"USAF\"\n" +
                            "}"))
                    .andExpect(status().isCreated())
                    .andReturn();

            UUID id = new ObjectMapper().readValue(result.getResponse().getContentAsString(), PersonDto.class).getId();

            // person PATCH only accepts application/json-patch+json with same contraints
            assertThrows(Exception.class, () ->
                    mockMvc.perform(patch(ENDPOINT + "/{id}", id.toString())
                            .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                            .header(XFCC_HEADER_NAME, XFCC_HEADER)
                            .contentType("application/json-patch+json")
                            .content("[\n" +
                                    "  { \"op\": \"replace\", \"path\": \"/firstName\", \"value\": \"" + StringUtils.repeat('J', 256) + "\" }" +
                                    "]\n"))
            );
        }
    }

    @Nested
    class TestOrganizationInputs {

        private static final String ENDPOINT = "/v2/organization";

        /**
         * The fields for an Organization - that are validated - are:
         *  leader - must be a proper UUID
         *  members - must be an array of proper UUIDs
         *  parentOrganization - must be a proper UUID
         *  subordinateOrganizations - must be an array of proper UUIDs
         *  name - limited to 255 chars
         *  orgType - enumeration of SQUADRON,GROUP,FLIGHT,WING,OTHER_USAF,ORGANIZATION
         *  branchType - must be OTHER,USA,USAF,USMC,USN,USSF, or USCG
         */
        @Test
        void testOrgInputs() throws Exception {

            // organization POST only accepts application/json
            mockMvc.perform(post(ENDPOINT)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("{\n" +
                            "    \"name\": \"Test\",\n" +
                            "    \"branchType\": \"USAF\",\n" +
                            "    \"orgType\": \"Squadron\"\n" +
                            "}"))
                    .andExpect(status().isUnsupportedMediaType());

            // organization PUT only accepts application/json
            mockMvc.perform(put(ENDPOINT + "/{id}", UUID.randomUUID())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("{\n" +
                            "    \"name\": \"Test\",\n" +
                            "    \"branchType\": \"USAF\",\n" +
                            "    \"orgType\": \"Squadron\"\n" +
                            "}"))
                    .andExpect(status().isUnsupportedMediaType());

            // organization PATCH only accepts application/json-patch+json
            mockMvc.perform(patch(ENDPOINT + "/{id}", UUID.randomUUID())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("{\n" +
                            "    \"name\": \"Test\",\n" +
                            "    \"branchType\": \"USAF\",\n" +
                            "    \"orgType\": \"Squadron\"\n" +
                            "}"))
                    .andExpect(status().isUnsupportedMediaType());

            // organization POST only accepts valid UUIDs for UUID fields
            mockMvc.perform(post(ENDPOINT)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\n" +
                            "    \"name\": \"Test\",\n" +
                            "    \"branchType\": \"USAF\",\n" +
                            "    \"leader\": \"123\",\n" +
                            "    \"orgType\": \"Squadron\"\n" +
                            "}"))
                    .andExpect(status().isBadRequest());

            // organization POST only accepts valid UUIDs for array-of-UUID fields
            mockMvc.perform(post(ENDPOINT)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\n" +
                            "    \"name\": \"Test\",\n" +
                            "    \"branchType\": \"USAF\",\n" +
                            "    \"subordinateOrganizations\": \"123\",\n" +
                            "    \"orgType\": \"Squadron\"\n" +
                            "}"))
                    .andExpect(status().isBadRequest());

            // organization POST only accepts valid unit types
            mockMvc.perform(post(ENDPOINT)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\n" +
                            "    \"name\": \"Test\",\n" +
                            "    \"branchType\": \"USAF\",\n" +
                            "    \"orgType\": \"Platoon\"\n" +
                            "}"))
                    .andExpect(status().isBadRequest());

            // organization POST only accepts names under 255 chars
            assertThrows(Exception.class, () -> mockMvc.perform(post(ENDPOINT)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\n" +
                            "    \"name\":\"" + StringUtils.repeat('J', 256) + "\",\n" +
                            "    \"branchType\": \"USAF\",\n" +
                            "    \"orgType\": \"Squadron\"\n" +
                            "}")));

            MvcResult result = mockMvc.perform(post(ENDPOINT)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\n" +
                            "    \"name\": \"Test\",\n" +
                            "    \"branchType\": \"USAF\",\n" +
                            "    \"orgType\": \"Squadron\"\n" +
                            "}"))
                    .andExpect(status().isCreated())
                    .andReturn();

            UUID id = new ObjectMapper().readValue(result.getResponse().getContentAsString(), OrganizationDto.class).getId();

            // organization PUT only accepts names under 255 chars (same as POST contraints)
            assertThrows(Exception.class, () -> mockMvc.perform(put(ENDPOINT + "/{id}", id)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\n" +
                            "    \"id\": \"" + id + "\",\n" +
                            "    \"name\":\"" + StringUtils.repeat('J', 256) + "\",\n" +
                            "    \"branchType\": \"USAF\",\n" +
                            "    \"orgType\": \"Squadron\"\n" +
                            "}")));

            // organization PATCH subject to same constraints - needs valid UUID field
            mockMvc.perform(patch(ENDPOINT + "/{id}", id)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType("application/json-patch+json")
                    .content("{\n" +
                            "    \"id\":\"blah\",\n" +
                            "    \"name\":\"test\",\n" +
                            "    \"branchType\": \"USAF\",\n" +
                            "    \"orgType\": \"Squadron\"\n" +
                            "}"))
                    .andExpect(status().isBadRequest());
        }

    }

    @Nested
    class TestAppClientInputs {

        private static final String ENDPOINT = "/v2/app-client";

        /**
         * The fields for an AppClient - that are validated - are:
         *  name - must be present and less than 255 chars
         *  privileges - must be array of PrivilegeDto (consists of { Long-Integer and String name })
         */
        @Test
        void testAppClientDtoInputs() throws Exception {

            // app-client POST only accepts application/json
            mockMvc.perform(post(ENDPOINT)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("{\n" +
                            "    \"name\": \"Test\"" +
                            "}"))
                    .andExpect(status().isUnsupportedMediaType());

            // app-client PUT only accepts application/json
            mockMvc.perform(put(ENDPOINT + "/{id}", UUID.randomUUID())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("{\n" +
                            "    \"name\": \"Test\"" +
                            "}"))
                    .andExpect(status().isUnsupportedMediaType());

            // app-client POST/PATCH requires a name to not be blank
            mockMvc.perform(post(ENDPOINT)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\n" +
                            "    \"name\": \"\"" +
                            "}"))
                    .andExpect(status().isBadRequest());

            // app-client POST with invalid/malformed privilege
            mockMvc.perform(post(ENDPOINT)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\n" +
                            "    \"name\": \"Test\"," +
                            "    \"privileges\": [ { \"id\": false, \"name\" : \"WRITE\" }]" +
                            "}"))
                    .andExpect(status().isBadRequest());

            // app-client POST/PATCH throws exception for over 255 chars for name
            assertThrows(Exception.class, () -> mockMvc.perform(post(ENDPOINT)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\n" +
                            "    \"name\": \"" + StringUtils.repeat('J', 256) +"\"" +
                            "}")));
        }
    }

    @Nested
    class TestScratchStorageAppRegistry {

        private static final String ENDPOINT = "/v2/scratch/apps";

        /**
         * The fields for an Scratch Storage App - that are validated - are:
         *  appName - must be present and less than 255 chars
         *  appHasImplicitRead - optional, but must be a boolean if present
         */
        @Test
        void testScratchStorageInputs() throws Exception {

            // scratch storage POST only accepts application/json
            mockMvc.perform(post(ENDPOINT)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("{\n" +
                            "    \"appName\": \"Test\"" +
                            "}"))
                    .andExpect(status().isUnsupportedMediaType());

            // scratch-storage PUT only accepts application/json
            mockMvc.perform(put(ENDPOINT + "/{id}", UUID.randomUUID())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("{\n" +
                            "    \"appName\": \"Test\"" +
                            "}"))
                    .andExpect(status().isUnsupportedMediaType());

            // scratch storage POST/PUT throws exception for over 255 chars for name
            assertThrows(Exception.class, () -> mockMvc.perform(post(ENDPOINT)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\n" +
                            "    \"appName\": \"" + StringUtils.repeat('J', 256) +"\"" +
                            "}")));

            // scratch storage POST only accepts valid boolean
            mockMvc.perform(post(ENDPOINT)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\n" +
                            "    \"appName\": \"Test\"," +
                            "    \"appHasImplicitRead\": \"Test\"" +
                            "}"))
                    .andExpect(status().isBadRequest());

            // scratch storage POST only accepts valid priv array
            mockMvc.perform(post(ENDPOINT)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\n" +
                            "    \"appName\": \"Test\"," +
                            "    \"appHasImplicitRead\": true," +
                            "    \"userPrivs\": [ { \"id\": \"123\", \"emailAddress\": \"cz@test.com\", privs: []" +
                            "}"))
                    .andExpect(status().isBadRequest());

        }
    }

    @Nested
    class TestDashboardUserInputs {

        private static final String ENDPOINT = "/v2/dashboard-users";

        /**
         * The fields for a Dashboard User - that are validated - are:
         *  email - must be present, valid email less than 255 chars
         *  privileges - must be array of PrivilegeDto (consists of { Long-Integer and String name })
         */
        @Test
        void testDashboardUserInputs() throws Exception {

            // dashboard user POST only accepts application/json
            mockMvc.perform(post(ENDPOINT)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("{\n" +
                            "    \"email\": \"\"" +
                            "}"))
                    .andExpect(status().isUnsupportedMediaType());

            // dashboard user PUT only accepts application/json
            mockMvc.perform(put(ENDPOINT + "/{id}", UUID.randomUUID())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("{\n" +
                            "    \"email\": \"\"" +
                            "}"))
                    .andExpect(status().isUnsupportedMediaType());

            // dashboard POST/PUT throws exception for over 255 chars for name
            mockMvc.perform(post(ENDPOINT)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\n" +
                            "    \"email\": \"jon@test." + StringUtils.repeat('J', 256) + "\"" +
                            "}"))
                    .andExpect(status().isBadRequest());

            // dashboard POST email is valid
            mockMvc.perform(post(ENDPOINT)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\n" +
                            "    \"email\": \"Test\"" +
                            "}"))
                    .andExpect(status().isBadRequest());

            // dashboard POST only accepts valid priv array
            mockMvc.perform(post(ENDPOINT)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\n" +
                            "    \"email\": \"user@user.com\"," +
                            "    \"privileges\": [ { \"id\": false, \"name\" : \"WRITE\" }]" +
                            "}"))
                    .andExpect(status().isBadRequest());

        }
    }

    @Nested
    class TestAppSourceInputs {

        private static final String ENDPOINT = "/v2/app-source";

        /**
         * The fields for an App Source - that are validated - are:
         *  name - must be present, valid email less than 255 chars
         */
        @Test
        void testAppSourceInputs() throws Exception {
            // app source POST only accepts application/json
            mockMvc.perform(post(ENDPOINT)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("{\n" +
                            "    \"name\": \"\"" +
                            "}"))
                    .andExpect(status().isUnsupportedMediaType());

            // app source PUT only accepts application/json
            mockMvc.perform(put(ENDPOINT + "/{id}", UUID.randomUUID())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("{\n" +
                            "    \"name\": \"\"" +
                            "}"))
                    .andExpect(status().isUnsupportedMediaType());

            // app source POST only accepts non blank/null name
            mockMvc.perform(post(ENDPOINT)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\n" +
                            "    \"name\": \"\"" +
                            "}"))
                    .andExpect(status().isBadRequest());

            // app source POST only accepts name string less than 255 chars
            assertThrows(Exception.class, () -> mockMvc.perform(post(ENDPOINT)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\n" +
                            "    \"name\": \"" + StringUtils.repeat('J', 256) +"\"" +
                            "}")));
        }
    }

    @Nested
    class TestSubscriberTests {

        private static final String ENDPOINT = "/v2/subscriptions";

        /**
         * The fields for an Pub Sub Subscriptions - that are validated - are:
         * name - must be present, valid email less than 255 chars
         */
        @Test
        void testPubSubInputs() throws Exception {
            // pub sub POST only accepts application/json
            mockMvc.perform(post(ENDPOINT)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("{\n" +
                            "    \"subscriberAddress\": \"\"," +
                            "    \"subscribedEvent\": \"\"" +
                            "}"))
                    .andExpect(status().isUnsupportedMediaType());

            // pub sub POST only takes enumerated type for event type
            mockMvc.perform(post(ENDPOINT)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\n" +
                            "    \"subscriberAddress\": \"http://localhost:8080/\"," +
                            "    \"subscribedEvent\": \"blah\"" +
                            "}"))
                    .andExpect(status().isBadRequest());

            // pub sub POST rejects outside web address
            mockMvc.perform(post(ENDPOINT)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\n" +
                            "    \"subscriberAddress\": \"http://www.google.com/\"," +
                            "    \"subscribedEvent\": \"PERSON_CHANGE\"" +
                            "}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
