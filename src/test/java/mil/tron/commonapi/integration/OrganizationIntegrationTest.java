package mil.tron.commonapi.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;

import mil.tron.commonapi.dto.FilterDto;
import mil.tron.commonapi.dto.OrganizationDto;
import mil.tron.commonapi.dto.OrganizationDtoResponseWrapper;
import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.dto.organizations.Squadron;
import mil.tron.commonapi.dto.response.pagination.Pagination;
import mil.tron.commonapi.dto.response.pagination.PaginationLink;
import mil.tron.commonapi.dto.response.pagination.PaginationWrappedResponse;
import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.orgtypes.Unit;
import mil.tron.commonapi.repository.OrganizationMetadataRepository;
import mil.tron.commonapi.repository.OrganizationRepository;
import mil.tron.commonapi.repository.PersonRepository;
import mil.tron.commonapi.repository.filter.FilterCondition;
import mil.tron.commonapi.repository.filter.FilterCriteria;
import mil.tron.commonapi.repository.filter.QueryOperator;
import mil.tron.commonapi.service.OrganizationService;
import mil.tron.commonapi.service.PersonConversionOptions;
import mil.tron.commonapi.service.PersonService;

import org.assertj.core.util.Lists;
import org.hamcrest.Matchers;
import org.json.JSONArray;
import org.json.JSONObject;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.transaction.Transactional;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties", properties = { "efa-enabled=false" })
@ActiveProfiles(value = { "development", "test" })  // enable at least dev so we get tracing enabled for full integration
@AutoConfigureMockMvc
public class OrganizationIntegrationTest {

    private static final String ENDPOINT = "/v1/organization/";
    private static final String ENDPOINT_V2 = "/v2/organization/";
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

    @Autowired
    private PersonRepository personRepository;

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
        mockMvc.perform(post(ENDPOINT_V2 + "organizations").contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(newOrganizations))).andExpect(status().isCreated())
                .andExpect(result -> assertEquals(3, OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(),
                        OrganizationDtoResponseWrapper.class).getData().size()));

        // now try to add again one that already has an existing name
        OrganizationDto s4 = new OrganizationDto();
        s4.setName(organization.getName());
        mockMvc.perform(post(ENDPOINT_V2 + "organizations").contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(s4)))).andExpect(status().isConflict());

        // test pagination
        mockMvc.perform(get(ENDPOINT + "?page=0&size=")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        mockMvc.perform(get(ENDPOINT + "?page=1&size=2")).andExpect(status().isOk())
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
        theOrg.setMembersUUID(List.of(p1.getId(), p2.getId()));
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

    @Nested
    class OrgPatchTests {

        private final Squadron existingOrgDto;
        private final Squadron existingParentOrg;
        private final Squadron existingChildOrg;
        private final Person existingPerson;
        private final UUID orgId = UUID.randomUUID();
        private final UUID parentOrgId = UUID.randomUUID();
        private final UUID childOrgId = UUID.randomUUID();

        public OrgPatchTests() {
            this.existingPerson = Person.builder()
                    .id(UUID.randomUUID())
                    .firstName("First")
                    .lastName("Last")
                    .build();
            this.existingOrgDto = new Squadron();
            this.existingOrgDto.setId(orgId);
            this.existingOrgDto.setName("Org Name");
            this.existingOrgDto.setBranchType(Branch.USAF);
            this.existingOrgDto.setOrgType(Unit.SQUADRON);
            this.existingOrgDto.setMembers(Set.of(this.existingPerson));
            this.existingOrgDto.setLeader(this.existingPerson);
            this.existingOrgDto.setPas("Pas");
            this.existingOrgDto.setPas("pasValue");

            this.existingParentOrg = new Squadron();
            this.existingParentOrg.setId(parentOrgId);
            this.existingParentOrg.setName("Parent Org Name");
            this.existingParentOrg.setBranchType(Branch.USAF);
            this.existingParentOrg.setOrgType(Unit.SQUADRON);

            this.existingChildOrg = new Squadron();
            this.existingChildOrg.setId(childOrgId);
            this.existingChildOrg.setName("Child Org Name");
            this.existingChildOrg.setBranchType(Branch.USAF);
            this.existingChildOrg.setOrgType(Unit.SQUADRON);
        }

        @BeforeEach
        void beforeEach() {
            PersonDto personDto = personService.convertToDto(this.existingPerson, new PersonConversionOptions());
            personDto.setRank("Capt");
            personDto.setBranch(Branch.USAF);
            personService.createPerson(personDto);
            organizationService.createOrganization(this.existingParentOrg);
            Optional<Organization> parentOrg = organizationRepository.findById(this.existingParentOrg.getId());
            existingOrgDto.setParentOrganization(parentOrg.get());
            organizationService.createOrganization(this.existingOrgDto);
        }

        @Test
        @Transactional
        void testPatchOrgLeader() throws Exception {
            UUID newLeaderId = UUID.randomUUID();
            PersonDto newLeader = personService.convertToDto(Person.builder()
                    .id(newLeaderId)
                    .build(), new PersonConversionOptions());
            newLeader.setRank("Capt");
            newLeader.setBranch(Branch.USAF);
            personService.createPerson(newLeader);
            JSONArray contentArray = new JSONArray();
            JSONObject content = new JSONObject();
            content.put("op", "replace");
            content.put("path", "/leader");
            content.put("value", newLeaderId);
            contentArray.put(content);
            MvcResult result = mockMvc.perform(patch(ENDPOINT + "{id}", this.existingOrgDto.getId())
                    .contentType("application/json-patch+json")
                    .content(contentArray.toString()))
                    .andExpect(status().isOk())
                    .andReturn();
            assertTrue(result.getResponse().getContentAsString().contains(newLeaderId.toString()));

            // now delete the leader as a person
            personService.deletePerson(newLeaderId);

            // make sure they're not on this org anymore and the org is still there (bug TRONAPI-386)
            mockMvc.perform(get(ENDPOINT + "{id}", this.existingOrgDto.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.leader", equalTo(null)))
                    .andExpect(jsonPath("$.members", Matchers.not(Matchers.contains(newLeaderId.toString()))));

        }

        @Test
        @Transactional
        void testPatchAddOrgMembers() throws Exception {

            UUID newMemberId = UUID.randomUUID();
            PersonDto newMember = personService.convertToDto(Person.builder()
                    .id(newMemberId)
                    .build(), new PersonConversionOptions());
            newMember.setRank("Capt");
            newMember.setBranch(Branch.USAF);
            personService.createPerson(newMember);

            JSONArray contentArray = new JSONArray();
            JSONObject content = new JSONObject();
            content.put("op", "add");
            content.put("path", "/members/-");
            content.put("value", newMemberId);
            contentArray.put(content);
            MvcResult result = mockMvc.perform(patch(ENDPOINT + "{id}", this.existingOrgDto.getId())
                    .contentType("application/json-patch+json")
                    .content(contentArray.toString()))
                    .andExpect(status().isOk())
                    .andReturn();
            Optional<Organization> updatedOrg = organizationRepository.findById(this.existingOrgDto.getId());
            Set<Person> orgMembers = updatedOrg.get().getMembers();
            int numberOfMembers = orgMembers.size();
            assertEquals(2, numberOfMembers);
        }

        @Test
        @Transactional
        void testPatchOrgParentOrg() throws Exception {
            JSONArray contentArray = new JSONArray();
            JSONObject content = new JSONObject();
            content.put("op", "remove");
            content.put("path", "/parentOrganization");
            contentArray.put(content);
            MvcResult result = mockMvc.perform(patch(ENDPOINT + "{id}", this.existingOrgDto.getId())
                    .contentType("application/json-patch+json")
                    .content(contentArray.toString()))
                    .andExpect(status().isOk())
                    .andReturn();
            Optional<Organization> updatedOrg = organizationRepository.findById(this.existingOrgDto.getId());
            assertTrue(updatedOrg.get().getParentOrganization() == null);
        }

        @Test
        @Transactional
        void testPatchNameTest() throws Exception {
            JSONArray contentArray = new JSONArray();

            JSONObject testContent = new JSONObject();
            testContent.put("op", "test");
            testContent.put("path", "/name");
            testContent.put("value", "Not the Name");
            contentArray.put(testContent);

            JSONObject replaceContent = new JSONObject();
            replaceContent.put("op", "replace");
            replaceContent.put("path", "/name");
            replaceContent.put("value", "New Name");
            contentArray.put(replaceContent);

            MvcResult result = mockMvc.perform(patch(ENDPOINT + "{id}", this.existingOrgDto.getId())
                    .contentType("application/json-patch+json")
                    .content(contentArray.toString()))
                    .andExpect(status().is4xxClientError())
                    .andReturn();
            Optional<Organization> updatedOrg = organizationRepository.findById(this.existingOrgDto.getId());
            assertEquals("Org Name", updatedOrg.get().getName());
        }

        @AfterEach
        void afterEach() throws Exception {
            organizationService.deleteOrganization(orgId);
            personRepository.deleteAll();
        }
    }
    
    @Test
    @Rollback
    @Transactional
    void testWrappedResponse() throws Exception {

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
        theOrg.setMembersUUID(List.of(p1.getId(), p2.getId()));
        organizationService.createOrganization(theOrg);

        parent.setSubOrgsUUID(Set.of(theOrg.getId()));
        organizationService.updateOrganization(parent.getId(), parent);
       
        
        PaginationWrappedResponse<List<OrganizationDto>> response = 
				PaginationWrappedResponse.<List<OrganizationDto>>builder()
				.data(Lists.newArrayList(parent))
				.pagination(Pagination.builder()
						.page(0)
						.size(1)
						.totalElements(2L)
						.totalPages(2)
						.links(PaginationLink.builder()
								.next("http://localhost" + ENDPOINT_V2 + "?people=id,firstName&organizations=id,name&page=1&size=1")
								.last("http://localhost" + ENDPOINT_V2 + "?people=id,firstName&organizations=id,name&page=1&size=1")
								.build())
						.build())
				.build();
        

        mockMvc.perform(get(ENDPOINT_V2 + "?people=id,firstName&organizations=id,name&size=1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].id").value(response.getData().get(0).getId().toString()))
            .andExpect(jsonPath("$.pagination.page").value(response.getPagination().getPage()))
            .andExpect(jsonPath("$.pagination.size").value(response.getPagination().getSize()))
            .andExpect(jsonPath("$.pagination.totalElements").value(response.getPagination().getTotalElements()))
            .andExpect(jsonPath("$.pagination.totalPages").value(response.getPagination().getTotalPages()));
        
        mockMvc.perform(get(ENDPOINT_V2 + "?size=1"))
        	.andExpect(status().isOk())
	        .andExpect(jsonPath("$.data", hasSize(1)))
	        .andExpect(jsonPath("$.data[0].id").value(response.getData().get(0).getId().toString()))
	        .andExpect(jsonPath("$.pagination.page").value(response.getPagination().getPage()))
	        .andExpect(jsonPath("$.pagination.size").value(response.getPagination().getSize()))
	        .andExpect(jsonPath("$.pagination.totalElements").value(response.getPagination().getTotalElements()))
	        .andExpect(jsonPath("$.pagination.totalPages").value(response.getPagination().getTotalPages()));
        
        mockMvc.perform(get(ENDPOINT_V2 + "?people=id,firstName&organizations=id,name&size=1&branch=usaf"))
	        .andExpect(status().isOk())
	        .andExpect(jsonPath("$.data", hasSize(1)))
	        .andExpect(jsonPath("$.data[0].id").value(response.getData().get(0).getId().toString()))
	        .andExpect(jsonPath("$.pagination.page").value(response.getPagination().getPage()))
	        .andExpect(jsonPath("$.pagination.size").value(response.getPagination().getSize()))
	        .andExpect(jsonPath("$.pagination.totalElements").value(response.getPagination().getTotalElements()))
	        .andExpect(jsonPath("$.pagination.totalPages").value(response.getPagination().getTotalPages()));
        
        mockMvc.perform(get(ENDPOINT_V2 + "?size=1&branch=usaf"))
	        .andExpect(status().isOk())
	        .andExpect(jsonPath("$.data", hasSize(1)))
	        .andExpect(jsonPath("$.data[0].id").value(response.getData().get(0).getId().toString()))
	        .andExpect(jsonPath("$.pagination.page").value(response.getPagination().getPage()))
	        .andExpect(jsonPath("$.pagination.size").value(response.getPagination().getSize()))
	        .andExpect(jsonPath("$.pagination.totalElements").value(response.getPagination().getTotalElements()))
	        .andExpect(jsonPath("$.pagination.totalPages").value(response.getPagination().getTotalPages()));

    }
    
    @Transactional
    @Rollback
    @Test
    void testOrganizationPostFilter() throws Exception {
    	/**
    	 * Save everything to database
    	 */
    	PersonDto person = PersonDto.builder()
                .firstName("test")
                .lastName("member")
                .email("test@member.com")
                .rank("CIV")
                .branch(Branch.USAF)
                .dodid("12345")
                .build();
    	
    	PersonDto person1 = PersonDto.builder()
                .firstName("1")
                .lastName("2")
                .email("1@2.com")
                .rank("CIV")
                .branch(Branch.OTHER)
                .dodid("34567")
                .build();
    	
    	personService.createPerson(person);
    	personService.createPerson(person1);
		
		OrganizationDto parentOrgPersonMember = OrganizationDto.builder()
				.id(UUID.randomUUID())
	            .name("TestOrg1")
	            .members(List.of(person.getId()))
	            .build();
		
        organizationService.createOrganization(parentOrgPersonMember);
        
        OrganizationDto subOrgPerson1Leader = OrganizationDto.builder()
        		.id(UUID.randomUUID())
	            .name("TestOrg2")
	            .leader(person1.getId())
	            .parentOrganization(parentOrgPersonMember.getId())
	            .build();
        
        organizationService.createOrganization(subOrgPerson1Leader);
        parentOrgPersonMember.setSubOrgsUUID(Set.of(subOrgPerson1Leader.getId()));
        organizationService.updateOrganization(parentOrgPersonMember.getId(), parentOrgPersonMember);
        
        /**
         * Tests below here are for specific Organization fields that will get transformed to join attributes
         * parentOrganization, subordinateOrganizations, members, leader
         */
        
        // parentOrganization
        FilterDto filterDto = new FilterDto();
        FilterCriteria criteria = FilterCriteria.builder()
				.field(OrganizationDto.PARENT_ORG_FIELD)
				.conditions(List.of(FilterCondition.builder()
										.operator(QueryOperator.EQUALS)
										.value(parentOrgPersonMember.getId().toString())
										.build()))
				.build();
		filterDto.setFilterCriteria(Lists.newArrayList(criteria));
		
		mockMvc.perform(post(ENDPOINT_V2 + "/filter")
				.contentType(MediaType.APPLICATION_JSON)
				.content(OBJECT_MAPPER.writeValueAsString(filterDto)))
		.andExpect(status().isOk())
        .andExpect(jsonPath("$.data", hasSize(1)))
        .andExpect(jsonPath("$.data[0].id", is(subOrgPerson1Leader.getId().toString())));
		
		// subordinateOrganizations
        criteria = FilterCriteria.builder()
				.field(OrganizationDto.SUB_ORGS_FIELD)
				.conditions(List.of(FilterCondition.builder()
										.operator(QueryOperator.EQUALS)
										.value(subOrgPerson1Leader.getId().toString())
										.build()))
				.build();
		filterDto.setFilterCriteria(Lists.newArrayList(criteria));
		
		mockMvc.perform(post(ENDPOINT_V2 + "/filter")
				.contentType(MediaType.APPLICATION_JSON)
				.content(OBJECT_MAPPER.writeValueAsString(filterDto)))
		.andExpect(status().isOk())
        .andExpect(jsonPath("$.data", hasSize(1)))
        .andExpect(jsonPath("$.data[0].id", is(parentOrgPersonMember.getId().toString())));
		
		// members
		criteria = FilterCriteria.builder()
				.field(OrganizationDto.MEMBERS_FIELD)
				.conditions(List.of(FilterCondition.builder()
										.operator(QueryOperator.EQUALS)
										.value(person.getId().toString())
										.build()))
				.build();
		filterDto.setFilterCriteria(Lists.newArrayList(criteria));
		
		mockMvc.perform(post(ENDPOINT_V2 + "/filter")
				.contentType(MediaType.APPLICATION_JSON)
				.content(OBJECT_MAPPER.writeValueAsString(filterDto)))
		.andExpect(status().isOk())
        .andExpect(jsonPath("$.data", hasSize(1)))
        .andExpect(jsonPath("$.data[0].id", is(parentOrgPersonMember.getId().toString())));
		
		// leader
		criteria = FilterCriteria.builder()
				.field(OrganizationDto.LEADER_FIELD)
				.conditions(List.of(FilterCondition.builder()
										.operator(QueryOperator.EQUALS)
										.value(person1.getId().toString())
										.build()))
				.build();
		filterDto.setFilterCriteria(Lists.newArrayList(criteria));
		
		mockMvc.perform(post(ENDPOINT_V2 + "/filter")
				.contentType(MediaType.APPLICATION_JSON)
				.content(OBJECT_MAPPER.writeValueAsString(filterDto)))
		.andExpect(status().isOk())
        .andExpect(jsonPath("$.data", hasSize(1)))
        .andExpect(jsonPath("$.data[0].id", is(subOrgPerson1Leader.getId().toString())));
		
    }

    private static String resource(String name) throws IOException {
        return Resources.toString(Resources.getResource("integration/" + name), StandardCharsets.UTF_8);
    }
}
