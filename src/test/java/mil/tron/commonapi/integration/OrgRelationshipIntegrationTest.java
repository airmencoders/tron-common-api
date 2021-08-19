package mil.tron.commonapi.integration;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.dto.OrganizationDto;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.orgtypes.Unit;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.OrganizationRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import org.assertj.core.util.Lists;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.transaction.Transactional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test that focuses on organizational relationships by using all the various
 * endpoints where we can modify/create parent and subordinate relationships.  This is a rather
 * complicated topic since we want to preserve a clean organizational relationship heirarchy where
 * parents cant be subordinates in the same organizational (family) tree and orgs cant be subordinate to
 * organizations across family trees.
 */

@ExtendWith(SpringExtension.class)
@SpringBootTest(properties = { "security.enabled=true" })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@AutoConfigureTestDatabase(replace = Replace.ANY)
@TestPropertySource(locations = "classpath:application-test.properties")
@ActiveProfiles(value = { "development", "test" })  // enable at least dev so we get tracing enabled for full integration
@AutoConfigureMockMvc
public class OrgRelationshipIntegrationTest {

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

    private static final String ENDPOINT_V1 = "/v1/organization/";
    private static final String ENDPOINT_V2 = "/v2/organization/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PrivilegeRepository privRepo;

    @Autowired
    private DashboardUserRepository dashRepo;

    @Autowired
    private OrganizationRepository organizationRepository;

    private DashboardUser admin;

    OrganizationDto parent, child1, child2, child3;

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

        parent = OrganizationDto
                .builder()
                .branchType(Branch.USAF)
                .orgType(Unit.WING)
                .name("Parent")
                .build();

        parent = OBJECT_MAPPER.readValue(mockMvc.perform(post(ENDPOINT_V2)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(parent)))
                .andExpect(status().isCreated())
                .andReturn()
                        .getResponse()
                        .getContentAsString(), OrganizationDto.class);

        child1 = OrganizationDto
                .builder()
                .branchType(Branch.USAF)
                .orgType(Unit.WING)
                .name("Child1")
                .build();

        child1 = OBJECT_MAPPER.readValue(mockMvc.perform(post(ENDPOINT_V2)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(child1)))
                .andExpect(status().isCreated())
                .andReturn()
                    .getResponse()
                    .getContentAsString(), OrganizationDto.class);

        child2 = OrganizationDto
                .builder()
                .branchType(Branch.USAF)
                .orgType(Unit.WING)
                .name("Child2")
                .build();

        child2 = OBJECT_MAPPER.readValue(mockMvc.perform(post(ENDPOINT_V2)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(child2)))
                .andExpect(status().isCreated())
                .andReturn()
                    .getResponse()
                    .getContentAsString(), OrganizationDto.class);

        child3 = OrganizationDto
                .builder()
                .branchType(Branch.USAF)
                .orgType(Unit.WING)
                .name("Child3")
                .build();

        child3 = OBJECT_MAPPER.readValue(mockMvc.perform(post(ENDPOINT_V2)
                .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                .header(XFCC_HEADER_NAME, XFCC_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(child3)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(), OrganizationDto.class);
    }

    /**
     * These tests use the POST (create) endpoint
     */
    @Nested
    class CreateTests {

        @Test
        void testCreationFailsForSameParentAndSubOrg() throws Exception {
            OrganizationDto child4 = OrganizationDto
                    .builder()
                    .branchType(Branch.USAF)
                    .orgType(Unit.WING)
                    .name("Child4")
                    .build();

            child4.setParentOrganizationUUID(child1.getId());
            child4.setSubOrgsUUID(Lists.newArrayList(child1.getId()));

            mockMvc.perform(post(ENDPOINT_V2)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(child4)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void testCreationFailsForParentAsDeepRelative() throws Exception {
            OrganizationDto child4 = OrganizationDto
                    .builder()
                    .branchType(Branch.USAF)
                    .orgType(Unit.WING)
                    .name("Child4")
                    .build();

            child2.setSubOrgsUUID(Lists.newArrayList(child3.getId()));
            child2 = OBJECT_MAPPER.readValue(mockMvc.perform(put(ENDPOINT_V2 + "{id}", child2.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(child2)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.parentOrganization", nullValue()))
                    .andExpect(jsonPath("$.subordinateOrganizations").value(contains(child3.getId().toString())))
                    .andReturn().getResponse().getContentAsString(), OrganizationDto.class);

            // test parent org is set implicitly of child3 from above operation
            child3 = OBJECT_MAPPER.readValue(mockMvc.perform(get(ENDPOINT_V2 + "{id}", child3.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.parentOrganization", equalTo(child2.getId().toString())))
                    .andReturn().getResponse().getContentAsString(), OrganizationDto.class);

            child3.setSubOrgsUUID(Lists.newArrayList(child1.getId()));
            mockMvc.perform(put(ENDPOINT_V2 + "{id}", child3.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(child3)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.parentOrganization", equalTo(child2.getId().toString())))
                    .andExpect(jsonPath("$.subordinateOrganizations", contains(child1.getId().toString())));

            // set "parent" as the parent to child2
            child2.setParentOrganizationUUID(parent.getId());
            mockMvc.perform(put(ENDPOINT_V2 + "{id}", child2.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(child2)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.parentOrganization", equalTo(parent.getId().toString())));

            // now create child4 with parent as a suborg and child3 as a parent, shouldn't work
            child4.setSubOrgsUUID(Lists.newArrayList(parent.getId()));
            child4.setParentOrganizationUUID(child3.getId());

            mockMvc.perform(post(ENDPOINT_V2)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(child4)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void testCreateOrganizationWithAlreadyExistsSubOrg() throws Exception {

            // test can create a new org with a suborg (that exists) already specified
            OrganizationDto child1 = OrganizationDto
                    .builder()
                    .branchType(Branch.USAF)
                    .orgType(Unit.SQUADRON)
                    .name("SubOrgSquadron")
                    .build();

            MvcResult result = mockMvc.perform(post(ENDPOINT_V2)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(child1)))
                    .andExpect(status().isCreated())
                    .andReturn();

            UUID childId = OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), OrganizationDto.class).getId();

            OrganizationDto newParent = OrganizationDto
                    .builder()
                    .branchType(Branch.USAF)
                    .orgType(Unit.WING)
                    .name("NewWing")
                    .subordinateOrganizations(Lists.newArrayList(childId))
                    .build();

            mockMvc.perform(post(ENDPOINT_V2)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(newParent)))
                    .andExpect(status().isCreated());

            // Test that a bad request will rollback the new org from persisting
            OrganizationDto failedParent = OrganizationDto
                    .builder()
                    .branchType(Branch.USAF)
                    .orgType(Unit.WING)
                    .name("FailedParent")
                    .subordinateOrganizations(Lists.newArrayList(UUID.randomUUID()))
                    .build();

            mockMvc.perform(post(ENDPOINT_V2)
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(failedParent)))
                    .andExpect(status().isNotFound());

            // verify change was rolled back
            assertFalse(organizationRepository.findAll()
                    .stream()
                    .map(Organization::getName)
                    .collect(Collectors.toList())
                    .contains("FailedParent"));
        }

    }

    /**
     * These tests use the PUT endpoint
     */
    @Nested
    class UpdateTests {

        @Test
        void testInvalidSameParentAndSubOrg() throws Exception {

            parent.setParentOrganizationUUID(child1.getId());
            parent.setSubOrgsUUID(Lists.newArrayList(child1.getId()));

            mockMvc.perform(put(ENDPOINT_V2 + "{id}", parent.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(parent)))
                    .andExpect(status().isBadRequest());

        }

        @Test
        void testInvalidSubOrgIsSubOrgElsewhere() throws Exception {

            parent.setSubOrgsUUID(Lists.newArrayList(child1.getId()));

            child2.setSubOrgsUUID(Lists.newArrayList(child1.getId()));
            mockMvc.perform(put(ENDPOINT_V2 + "{id}", parent.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(parent)))
                    .andExpect(status().isOk());

            mockMvc.perform(put(ENDPOINT_V2 + "{id}", child2.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(child2)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void testValidParent() throws Exception {

            parent.setSubOrgsUUID(Lists.newArrayList(child1.getId()));
            mockMvc.perform(put(ENDPOINT_V2 + "{id}", parent.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(parent)))
                    .andExpect(status().isOk());

            child2.setSubOrgsUUID(Lists.newArrayList(child3.getId()));
            mockMvc.perform(put(ENDPOINT_V2 + "{id}", child2.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(child2)))
                    .andExpect(status().isOk());

            // child3 is a suborg to child2 in another family tree, but still a valid parent
            //   for "parent" since it basically joins the two family trees
            parent.setParentOrganizationUUID(child3.getId());
            mockMvc.perform(put(ENDPOINT_V2 + "{id}", parent.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(parent)))
                    .andExpect(status().isOk());
        }

        @Test
        void testInvalidParentForDeepSubOrg() throws Exception {

            child2.setSubOrgsUUID(Lists.newArrayList(child3.getId()));
            child2 = OBJECT_MAPPER.readValue(mockMvc.perform(put(ENDPOINT_V2 + "{id}", child2.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(child2)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.parentOrganization", nullValue()))
                    .andExpect(jsonPath("$.subordinateOrganizations").value(contains(child3.getId().toString())))
                    .andReturn().getResponse().getContentAsString(), OrganizationDto.class);

            // test parent org is set implicitly of child3 from above operation
            child3 = OBJECT_MAPPER.readValue(mockMvc.perform(get(ENDPOINT_V2 + "{id}", child3.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.parentOrganization", equalTo(child2.getId().toString())))
                    .andReturn().getResponse().getContentAsString(), OrganizationDto.class);

            child3.setSubOrgsUUID(Lists.newArrayList(child1.getId()));
            mockMvc.perform(put(ENDPOINT_V2 + "{id}", child3.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(child3)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.parentOrganization", equalTo(child2.getId().toString())))
                    .andExpect(jsonPath("$.subordinateOrganizations", contains(child1.getId().toString())));

            // set "parent" as the parent to child2
            child2.setParentOrganizationUUID(parent.getId());
            mockMvc.perform(put(ENDPOINT_V2 + "{id}", child2.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(child2)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.parentOrganization", equalTo(parent.getId().toString())));

            // test parent org has "child2" as a suborg implicitly from the above operation
            parent = OBJECT_MAPPER.readValue(mockMvc.perform(get(ENDPOINT_V2 + "{id}", parent.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.subordinateOrganizations", contains(child2.getId().toString())))
                    .andReturn().getResponse().getContentAsString(), OrganizationDto.class);

            // this def should not work, child3 is down in the family tree several generations
            parent.setParentOrganizationUUID(child3.getId());
            mockMvc.perform(put(ENDPOINT_V2 + "{id}", parent.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(parent)))
                    .andExpect(status().isBadRequest());

            // now delete the parent org - should clear out itself as the parentorg of child2
            mockMvc.perform(delete(ENDPOINT_V2 + "{id}", parent.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get(ENDPOINT_V2 + "{id}", child2.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.parentOrganization", nullValue()));
        }

        @Test
        void testValidParentChange() throws Exception {

            // set up the chain: Parent->Child1->Child2
            // we should be able to set child2's parent to parent just fine

            child1.setParentOrganizationUUID(parent.getId());
            mockMvc.perform(put(ENDPOINT_V2 + "{id}", child1.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(child1)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.parentOrganization", equalTo(parent.getId().toString())));

            child2.setParentOrganizationUUID(child1.getId());
            mockMvc.perform(put(ENDPOINT_V2 + "{id}", child2.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(child2)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.parentOrganization", equalTo(child1.getId().toString())));

            // now change child2's parent to parent via json patch
            mockMvc.perform(patch(ENDPOINT_V2 + "{id}", child2.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType("application/json-patch+json")
                    .accept(MediaType.APPLICATION_JSON)
                    .content("[{\"op\":\"replace\",\"path\":\"/parentOrganization\",\"value\":\"" + parent.getId() + "\"}]"))
                    .andExpect(status().isOk());

        }
    }

    /**
     * These tests use the json patch endpoint
     */
    @Nested
    class PatchTests {

        @Test
        void testInvalidSameParentAndSubOrg() throws Exception {

            JSONObject content = new JSONObject();
            content.put("op","replace");
            content.put("path","/parentOrganization");
            content.put("value", child1.getId());
            JSONObject content2 = new JSONObject();
            content2.put("op", "add");
            content2.put("path", "/subordinateOrganizations/-");
            content2.put("value", child1.getId());
            JSONArray patch = new JSONArray();
            patch.put(content);
            patch.put(content2);

            mockMvc.perform(patch(ENDPOINT_V2 + "{id}", parent.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType("application/json-patch+json")
                    .accept(MediaType.APPLICATION_JSON)
                    .content(patch.toString()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void testInvalidSubOrgIsSubOrgElsewhere() throws Exception {

            JSONObject content = new JSONObject();
            content.put("op","add");
            content.put("path","/subordinateOrganizations/-");
            content.put("value", child1.getId());

            JSONArray patch = new JSONArray();
            patch.put(content);


            mockMvc.perform(patch(ENDPOINT_V2 + "{id}", parent.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType("application/json-patch+json")
                    .accept(MediaType.APPLICATION_JSON)
                    .content(patch.toString()))
                    .andExpect(status().isOk());

            mockMvc.perform(patch(ENDPOINT_V2 + "{id}", child2.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType("application/json-patch+json")
                    .accept(MediaType.APPLICATION_JSON)
                    .content(patch.toString()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void testValidParent() throws Exception {

            JSONObject content = new JSONObject();
            content.put("op","add");
            content.put("path","/subordinateOrganizations/-");
            content.put("value", child1.getId());

            JSONArray patch = new JSONArray();
            patch.put(content);

            mockMvc.perform(patch(ENDPOINT_V2 + "{id}", parent.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType("application/json-patch+json")
                    .accept(MediaType.APPLICATION_JSON)
                    .content(patch.toString()))
                    .andExpect(status().isOk());

            content = new JSONObject();
            content.put("op","add");
            content.put("path","/subordinateOrganizations/-");
            content.put("value", child3.getId());

            patch = new JSONArray();
            patch.put(content);

            mockMvc.perform(patch(ENDPOINT_V2 + "{id}", child2.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType("application/json-patch+json")
                    .accept(MediaType.APPLICATION_JSON)
                    .content(patch.toString()))
                    .andExpect(status().isOk());

            content = new JSONObject();
            content.put("op","replace");
            content.put("path","/parentOrganization");
            content.put("value", child3.getId());

            patch = new JSONArray();
            patch.put(content);

            parent.setParentOrganizationUUID(child3.getId());
            mockMvc.perform(patch(ENDPOINT_V2 + "{id}", parent.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType("application/json-patch+json")
                    .accept(MediaType.APPLICATION_JSON)
                    .content(patch.toString()))
                    .andExpect(status().isOk());
        }
    }

    /**
     * These tests use the patch/delete Add / Remove endpoints
     */
    @Nested
    class AddRemoveTests {

        @Test
        void testInvalidSameParentAndSubOrg() throws Exception {

            mockMvc.perform(patch(ENDPOINT_V1 + "{id}/subordinates", parent.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("[ \"" + child1.getId() + "\"]"))
                    .andExpect(status().isOk());

            mockMvc.perform(patch(ENDPOINT_V1 + "{id}", parent.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{ \"parentOrganization\": \"" + child1.getId() + "\" }"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void testParentUpdatedOnSubOrgRemoval() throws Exception {

            mockMvc.perform(patch(ENDPOINT_V1 + "{id}/subordinates", parent.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("[ \"" + child1.getId() + "\"]"))
                    .andExpect(status().isOk());

            mockMvc.perform(get(ENDPOINT_V2 + "{id}", child1.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.parentOrganization", equalTo(parent.getId().toString())));

            mockMvc.perform(delete(ENDPOINT_V1 + "{id}/subordinates", parent.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("[ \"" + child1.getId() + "\"]"))
                    .andExpect(status().isOk());

            // test that implicit removal of parent org was modified for child1 from the above action
            mockMvc.perform(get(ENDPOINT_V2 + "{id}", child1.getId())
                    .header(AUTH_HEADER_NAME, createToken(admin.getEmail()))
                    .header(XFCC_HEADER_NAME, XFCC_HEADER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.parentOrganization", nullValue()));
        }
    }

}
