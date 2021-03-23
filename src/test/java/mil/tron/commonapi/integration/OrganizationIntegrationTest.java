package mil.tron.commonapi.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import mil.tron.commonapi.dto.OrganizationDto;
import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.orgtypes.Unit;
import mil.tron.commonapi.repository.OrganizationMetadataRepository;
import mil.tron.commonapi.repository.OrganizationRepository;
import mil.tron.commonapi.service.OrganizationService;
import mil.tron.commonapi.service.PersonService;

import org.assertj.core.util.Lists;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import javax.transaction.Transactional;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
@AutoConfigureMockMvc
public class OrganizationIntegrationTest {

    private static final String ENDPOINT = "/v1/organization/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    private OrganizationDto organization;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private OrganizationMetadataRepository organizationMetadataRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private PersonService personService;

    @BeforeEach
    public void insertSquadron() throws Exception {
        organization = new OrganizationDto();
        organization.setName("TEST ORG");
        organization.setMembers(null);
    }

    @Test
    @Transactional
    @Rollback
    void testBulkAddOrganizations() throws Exception {

        OrganizationDto s2 = new OrganizationDto();
        s2.setName("TEST2");
        s2.setMembers(null);

        OrganizationDto s3 = new OrganizationDto();
        s3.setName("TEST3");
        s3.setMembers(null);

        List<OrganizationDto> newOrganizations = Lists.newArrayList(organization, s2, s3);

        // test go path for controller to db for adding build organizations, add 3 get
        // back 3
        mockMvc.perform(post(ENDPOINT + "organizations").contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(newOrganizations))).andExpect(status().isCreated())
                .andExpect(result -> assertEquals(3, OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(),
                        OrganizationDto[].class).length));

        // now try to add again one that already has an existing name
        OrganizationDto s4 = new OrganizationDto();
        s4.setName(organization.getName());
        mockMvc.perform(post(ENDPOINT + "organizations").contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(s4)))).andExpect(status().isConflict());

        // test pagination
        mockMvc.perform(get(ENDPOINT + "?page=1&limit=")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        mockMvc.perform(get(ENDPOINT + "?page=2&limit=2")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

    }

    @Test
    @Rollback
    @Transactional
    void testAncestryLogic() throws Exception {

        OrganizationDto greatGrandParent = new OrganizationDto();
        greatGrandParent.setName("Great Grandpa");
        OrganizationDto grandParent = new OrganizationDto();
        grandParent.setName("Grandpa");
        OrganizationDto parent = new OrganizationDto();
        parent.setName("Father");
        OrganizationDto theOrg = new OrganizationDto();
        theOrg.setName("Son");
        OrganizationDto legitSubOrg = new OrganizationDto();
        legitSubOrg.setName("Grandson");

        OrganizationDto messedUpOrg = new OrganizationDto();
        messedUpOrg.setName("Messed up");
        messedUpOrg.setParentOrganizationUUID(theOrg.getId());
        messedUpOrg.setSubOrgsUUID(Set.of(grandParent.getId()));

        mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(greatGrandParent))).andExpect(status().isCreated());

        mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(grandParent))).andExpect(status().isCreated());

        mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(parent))).andExpect(status().isCreated());

        mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(theOrg))).andExpect(status().isCreated());

        mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(legitSubOrg))).andExpect(status().isCreated());

        Map<String, UUID> attribs = new HashMap<>();
        attribs.put("parentOrganization", parent.getId());
        mockMvc.perform(patch(ENDPOINT + "{id}", theOrg.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(attribs))).andExpect(status().isOk());

        Map<String, UUID> attribs2 = new HashMap<>();
        attribs2.put("parentOrganization", grandParent.getId());
        mockMvc.perform(patch(ENDPOINT + "{id}", parent.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(attribs2))).andExpect(status().isOk());

        Map<String, UUID> attribs3 = new HashMap<>();
        attribs3.put("parentOrganization", greatGrandParent.getId());
        mockMvc.perform(patch(ENDPOINT + "{id}", grandParent.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(attribs3))).andExpect(status().isOk());

        // now try to set theOrgs subordinate organization to be the grandparent
        List<UUID> subOrgs = Lists.newArrayList(grandParent.getId());
        mockMvc.perform(patch(ENDPOINT + "{id}/subordinates", theOrg.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(subOrgs))).andExpect(status().isBadRequest());

        // now try to set theOrgs subordinate organization to be the son, be OK
        subOrgs = Lists.newArrayList(legitSubOrg.getId());
        mockMvc.perform(patch(ENDPOINT + "{id}/subordinates", theOrg.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(subOrgs))).andExpect(status().isOk());

        // now try to do a whole new POST with a violation present already in the
        // suborgs
        mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(messedUpOrg))).andExpect(status().isBadRequest());

        // now do a PUT update to 'theOrg' with the grandparent pre-populated in the
        // subOrgs, should reject
        theOrg.setParentOrganizationUUID(parent.getId());
        theOrg.setSubOrgsUUID(Set.of(grandParent.getId()));
        mockMvc.perform(put(ENDPOINT + "{id}", theOrg.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(theOrg))).andExpect(status().isBadRequest());
    }

    @Test
    @Rollback
    @Transactional
    void testCustomizationOfDtoReturn() throws Exception {

        OrganizationDto parent = new OrganizationDto();
        parent.setName("Father");
        parent.setOrgType(Unit.SQUADRON);
        parent.setBranchType(Branch.USAF);
        OrganizationDto theOrg = new OrganizationDto();
        theOrg.setName("Son");
        theOrg.setOrgType(Unit.SQUADRON);
        theOrg.setBranchType(Branch.USAF);

        PersonDto p1 = PersonDto.builder().id(UUID.randomUUID()).firstName("Donny").middleName("Dont").lastName("Does")
                .rank("Capt").branch(Branch.USAF).build();
        PersonDto p2 = PersonDto.builder().id(UUID.randomUUID()).firstName("John").middleName("Q").lastName("Public")
                .rank("Capt").branch(Branch.USAF).build();

        personService.createPerson(p1);
        personService.createPerson(p2);

        organizationService.createOrganization(parent);

        theOrg.setParentOrganizationUUID(parent.getId());
        theOrg.setMembersUUID(Set.of(p1.getId(), p2.getId()));
        organizationService.createOrganization(theOrg);

        parent.setSubOrgsUUID(Set.of(theOrg.getId()));
        organizationService.updateOrganization(parent.getId(), parent);

        // test org members have two fields - id,firstName
        mockMvc.perform(get(ENDPOINT + "{id}?people=id,firstName&organizations=id,name", theOrg.getId()))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals(2,
                        OBJECT_MAPPER.readTree(result.getResponse().getContentAsString()).get("members").get(0).size()))
                .andExpect(result -> assertTrue(OBJECT_MAPPER.readTree(result.getResponse().getContentAsString())
                        .get("members").get(0).has("id")))
                .andExpect(result -> assertTrue(OBJECT_MAPPER.readTree(result.getResponse().getContentAsString())
                        .get("members").get(0).has("firstName")));

        // test org parentOrganization has two fields - id,name
        mockMvc.perform(get(ENDPOINT + "{id}?people=id,firstName&organizations=id,name", theOrg.getId()))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals(2,
                        OBJECT_MAPPER.readTree(result.getResponse().getContentAsString()).get("parentOrganization")
                                .size()))
                .andExpect(result -> assertTrue(OBJECT_MAPPER.readTree(result.getResponse().getContentAsString())
                        .get("parentOrganization").has("id")))
                .andExpect(result -> assertTrue(OBJECT_MAPPER.readTree(result.getResponse().getContentAsString())
                        .get("parentOrganization").has("name")));

        // test parent org subordinateOrganizations has two fields - id,name
        mockMvc.perform(get(ENDPOINT + "{id}?flatten=true&people=id,firstName&organizations=id,name", parent.getId()))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals(2,
                        OBJECT_MAPPER.readTree(result.getResponse().getContentAsString())
                                .get("subordinateOrganizations").get(0).size()))
                .andExpect(result -> assertTrue(OBJECT_MAPPER.readTree(result.getResponse().getContentAsString())
                        .get("subordinateOrganizations").get(0).has("id")))
                .andExpect(result -> assertTrue(OBJECT_MAPPER.readTree(result.getResponse().getContentAsString())
                        .get("subordinateOrganizations").get(0).has("name")));

        // get all orgs make sure that we have two
        mockMvc.perform(get(ENDPOINT + "?people=id,firstName&organizations=id,name")).andExpect(status().isOk())
                .andExpect(result -> assertEquals(2,
                        OBJECT_MAPPER.readTree(result.getResponse().getContentAsString()).size()));

        // get all orgs that are Squadrons and are USAF, make sure that we have two
        mockMvc.perform(get(ENDPOINT + "?type=SQUADRON&branch=USAF&people=id,firstName&organizations=id,name"))
                .andExpect(status().isOk()).andExpect(result -> assertEquals(2,
                        OBJECT_MAPPER.readTree(result.getResponse().getContentAsString()).size()));

    }

    @Nested
    class MetadataTests {
        @Test
        public void testCreateOrganizationInvalidMetadata() throws Exception {
            mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                    .content(resource("invalidOrganization.json"))).andExpect(status().isBadRequest());
        }

        @Test
        public void testCreateSquadronMetadata() throws Exception {
            String id = OBJECT_MAPPER
                    .readTree(mockMvc
                            .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                                    .content(resource("newSquadron.json")))
                            .andExpect(status().isCreated()).andExpect(jsonPath("pas").value("test1")).andReturn()
                            .getResponse().getContentAsString())
                    .get("id").asText();

            mockMvc.perform(get(ENDPOINT + id)).andExpect(status().isOk()).andExpect(jsonPath("pas").value("test1"));

            mockMvc.perform(put(ENDPOINT + id).contentType(MediaType.APPLICATION_JSON)
                    .content(resource("updatedSquadron.json"))).andExpect(status().isOk())
                    .andExpect(jsonPath("pas").doesNotExist());

            mockMvc.perform(get(ENDPOINT + id)).andExpect(status().isOk()).andExpect(jsonPath("pas").doesNotExist());

            mockMvc.perform(patch(ENDPOINT + id).contentType(MediaType.APPLICATION_JSON)
                    .content(resource("patchedSquadron.json"))).andExpect(status().isOk())
                    .andExpect(jsonPath("pas").value("test2"));

            mockMvc.perform(get(ENDPOINT + id)).andExpect(status().isOk()).andExpect(jsonPath("pas").value("test2"));
        }

        @AfterEach
        public void metadataCleanup() {
            organizationMetadataRepository.deleteAll();
            organizationRepository.deleteAll();
        }
    }

    private static String resource(String name) throws IOException {
        return Resources.toString(Resources.getResource("integration/" + name), StandardCharsets.UTF_8);
    }
}
