package mil.tron.commonapi.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import mil.tron.commonapi.dto.*;
import mil.tron.commonapi.dto.response.pagination.Pagination;
import mil.tron.commonapi.dto.response.pagination.PaginationLink;
import mil.tron.commonapi.dto.response.pagination.PaginationWrappedResponse;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.orgtypes.Unit;
import mil.tron.commonapi.repository.filter.FilterCondition;
import mil.tron.commonapi.repository.filter.FilterCriteria;
import mil.tron.commonapi.repository.filter.QueryOperator;
import mil.tron.commonapi.repository.filter.RelationType;
import mil.tron.commonapi.service.PersonFindType;
import mil.tron.commonapi.service.PersonService;
import org.assertj.core.util.Lists;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest(properties = { "spring.liquibase.contexts=None" })
@TestPropertySource(locations = "classpath:application-test.properties", properties = { "efa-enabled=false"})
@ActiveProfiles(value = { "development", "test" })  // enable at least dev so we get tracing enabled for full integration
@AutoConfigureMockMvc
public class PersonIntegrationTest {
    private static final String ENDPOINT = "/v1/person/";
    private static final String ENDPOINT_V2 = "/v2/person/";
    private static final String ORGANIZATION = "/v1/organization/";
    private static final String ORGANIZATION_V2 = "/v2/organization/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private PersonService personService;

    @Transactional
    @Rollback
    @Test
    public void testBulkAddPeople() throws Exception {
        PersonDto person = new PersonDto();
        person.setId(UUID.randomUUID());
        person.setFirstName("John");
        person.setMiddleName("Hero");
        person.setLastName("Public");
        person.setEmail("john@test.com");
        person.setTitle("CAPT");
        person.setRank("Capt");
        person.setBranch(Branch.USAF);

        PersonDto a2 = new PersonDto();
        a2.setId(UUID.randomUUID());
        a2.setEmail("test1@test.com");
        a2.setTitle("SSGT");
        a2.setRank("SSgt");
        a2.setBranch(Branch.USAF);

        List<PersonDto> newPeople = Lists.newArrayList(
                person,
                a2
        );

        mockMvc.perform(post(ENDPOINT_V2 + "persons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(newPeople)))
                .andExpect(status().isCreated())
                .andExpect(result -> assertEquals(2, OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), PersonDtoResponseWrapper.class).getData().size()));

        PersonDto a3 = new PersonDto();
        a3.setEmail("test1@test.com");
        a3.setTitle("SSGT");
        a3.setRank("SSgt");
        a3.setBranch(Branch.USAF);

        // test that we can't add someone with a dup email
        mockMvc.perform(post(ENDPOINT_V2 + "persons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(a3))))
                .andExpect(status().isConflict());

        // make sure the entities we added were actually created
        mockMvc.perform(get(ENDPOINT + "/" + person.getId()))
            .andExpect(status().isOk());
        mockMvc.perform(get(ENDPOINT + "/" + a2.getId()))
            .andExpect(status().isOk());
            
        // test pagination
        mockMvc.perform(get(ENDPOINT + "?page=0&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get(ENDPOINT + "?page=1&size=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void testRollbackOnBulkFail() throws Exception {

        // test that the rollback of a bad bulk insert is completed

        List<PersonDto> peopleArray = Lists.newArrayList(PersonDto.builder()
                .email("p1@test.com")
                .build(), PersonDto.builder()
                .email("p1@test.com")
                .build(), PersonDto.builder()
                .email("p3@test.com")
                .build(), PersonDto.builder()
                .email("p4test.com")
                .build());

        mockMvc.perform(post(ENDPOINT_V2 + "persons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(peopleArray)))
                .andExpect(status().is(not(HttpStatus.CREATED)));

        mockMvc.perform(get(ENDPOINT_V2)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Transactional
    @Rollback
    @Test
    void testRemovePersonThatsLeaderInOrg() throws Exception {

        PersonDto person = PersonDto.builder()
            .firstName("test")
            .lastName("member")
            .email("test@member.com")
            .rank("CIV")
            .branch(Branch.USAF)
            .dodid("12345")
            .build();


        MvcResult result = mockMvc.perform(post(ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(OBJECT_MAPPER.writeValueAsString(person)))
            .andExpect(status().isCreated())
            .andReturn();

        UUID id = OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), PersonDto.class).getId();

        OrganizationDto org = OrganizationDto.builder()
            .name("TestOrg1")
            .build();

        MvcResult orgResult = mockMvc.perform(post(ORGANIZATION)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(org)))
                .andExpect(status().isCreated())
                .andReturn();

        OrganizationDto dtoObj = OBJECT_MAPPER.readValue(orgResult.getResponse().getContentAsString(), OrganizationDto.class);

        JSONObject obj = new JSONObject();
        obj.put("op", "replace");
        obj.put("path", "/leader");
        obj.put("value", id.toString());
        JSONArray array = new JSONArray();
        array.put(obj);

        mockMvc.perform(patch(ORGANIZATION_V2 + "{id}", dtoObj.getId())
                .contentType("application/json-patch+json")
                .content(array.toString()))
                .andExpect(status().isOk());

        array.remove(0);
        obj.remove("path");
        obj.remove("value");
        array.put(obj);
        mockMvc.perform(patch(ORGANIZATION_V2 + "{id}", dtoObj.getId())
                .contentType("application/json-patch+json")
                .content(array.toString()))
                .andExpect(status().isBadRequest());


        mockMvc.perform(delete(ENDPOINT + "{id}", id))
                .andExpect(status().isNoContent());

    }
    
    @Transactional
    @Rollback
    @Test
    void testPersonFind() throws Exception {
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
                .branch(Branch.USAF)
                .dodid("34567")
                .build();
                
                
		mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(OBJECT_MAPPER.writeValueAsString(person)));
		mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(OBJECT_MAPPER.writeValueAsString(person1)));
		
		// Try to filter by email
		mockMvc.perform(get(ENDPOINT + String.format("find/?findByField=email&value=%s", person.getEmail())))
			.andExpect(status().isOk())
			.andExpect(result -> assertThat(result.getResponse().getContentAsString())
					.isEqualTo(OBJECT_MAPPER.writeValueAsString(person)));
		
		// Try to filter by dodid
		mockMvc.perform(get(ENDPOINT + String.format("find/?findByField=dodid&value=%s", person1.getDodid())))
			.andExpect(status().isOk())
			.andExpect(result -> assertThat(result.getResponse().getContentAsString())
					.isEqualTo(OBJECT_MAPPER.writeValueAsString(person1)));
		
		// Try an invalid filter
		mockMvc.perform(get(ENDPOINT + String.format("find/?findByField=asdf&value=%s", person1.getDodid())))
			.andExpect(status().isBadRequest());
    }

    @Test
    public void testPersonMemberships() throws Exception {
        String userId = OBJECT_MAPPER.readTree(
            mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(OBJECT_MAPPER.writeValueAsString(
                PersonDto.builder()
                    .firstName("test")
                    .lastName("member")
                    .email("test@member.com")
                    .rank("CIV")
                    .branch(Branch.USAF)
                    .build())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()
            ).get("id").asText();

        String orgId = OBJECT_MAPPER.readTree(
            mockMvc.perform(post(ORGANIZATION).contentType(MediaType.APPLICATION_JSON).content(OBJECT_MAPPER.writeValueAsString(
                OrganizationDto.builder()
                    .name("member test org")
                    .orgType(Unit.ORGANIZATION)
                    .branchType(Branch.USAF)
                    .build())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()
            ).get("id").asText();

        mockMvc.perform(get(ENDPOINT + userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("organizationMemberships").doesNotExist())
            .andExpect(jsonPath("organizationLeaderships").doesNotExist());
            
        mockMvc.perform(get(ENDPOINT + userId + "?memberships=true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("organizationMemberships").isArray())
            .andExpect(jsonPath("organizationMemberships", hasSize(0)));
            
        mockMvc.perform(get(ENDPOINT + userId + "?leaderships=true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("organizationLeaderships").isArray())
            .andExpect(jsonPath("organizationLeaderships", hasSize(0)));
        
        mockMvc.perform(patch(ORGANIZATION + orgId + "/members").contentType(MediaType.APPLICATION_JSON).content("[\"" + userId + "\"]"))
            .andExpect(status().isOk());

        mockMvc.perform(get(ENDPOINT + userId + "?memberships=true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("organizationMemberships").isArray())
            .andExpect(jsonPath("organizationMemberships", hasItem(orgId)));

        mockMvc.perform(patch(ORGANIZATION + orgId).contentType(MediaType.APPLICATION_JSON).content("{\"leader\":\"" + userId + "\"}"))
            .andExpect(status().isOk());
            
        mockMvc.perform(get(ENDPOINT + userId + "?leaderships=true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("organizationLeaderships").isArray())
            .andExpect(jsonPath("organizationLeaderships", hasItem(orgId)));

        //cleanup
        mockMvc.perform(delete(ORGANIZATION + orgId)).andExpect(status().is2xxSuccessful());
        mockMvc.perform(delete(ENDPOINT + userId)).andExpect(status().is2xxSuccessful());
    }

    @Transactional
    @Rollback
    @Test
    public void testAirmanMetadata() throws Exception {
    	PersonDto person = new PersonDto();
        person.setId(UUID.randomUUID());
        person.setFirstName("John");
        person.setMiddleName("Hero");
        person.setLastName("Public");
        person.setEmail("john1@test.com");
        person.setTitle("CAPT");
        person.setMetaProperty("afsc", "11FX");
        person.setRank("CIV");
        person.setBranch(Branch.USAF);
        person.setMetaProperty("afsc", "99A");
        
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(person)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(person.getId().toString()));

        person.setMetaProperty("afsc", null);
        
        mockMvc.perform(put(ENDPOINT + person.getId()).contentType(MediaType.APPLICATION_JSON).content(OBJECT_MAPPER.writeValueAsString(person)))
        		.andExpect(status().isOk())
    			.andExpect(jsonPath("afsc").doesNotExist());
    }

    @Test
    public void testAirmanInvalidMetadata() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(resource("invalidAirman.json")))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testPersonInvalidMetadata() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(resource("invalidPerson.json")))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testInvalidRankGoesToUnknown() throws Exception {
        PersonDto person = new PersonDto();
        person.setId(UUID.randomUUID());
        person.setFirstName("John");
        person.setMiddleName("Hero");
        person.setLastName("Public");
        person.setEmail("john@test.com");
        person.setTitle("CAPT");
        person.setRank(null);
        person.setBranch(null);

        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(person)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rank", equalTo("Unk")))
                .andExpect(jsonPath("$.branch", equalTo("OTHER")));

        person.setRank("blah");
        person.setBranch(Branch.USSF);
        mockMvc.perform(put(ENDPOINT + "{id}", person.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(person)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank", equalTo("Unk")))
                .andExpect(jsonPath("$.branch", equalTo("OTHER")));

        JSONObject content = new JSONObject();
        content.put("op","replace");
        content.put("path","/rank");
        content.put("value", "invalid");
        JSONArray contentArray = new JSONArray();
        contentArray.put(content);
        mockMvc.perform(patch(ENDPOINT + "{id}", person.getId())
                .contentType("application/json-patch+json")
                .content(contentArray.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank", equalTo("Unk")))
                .andExpect(jsonPath("$.branch", equalTo("OTHER")));
        
        mockMvc.perform(delete(ENDPOINT + person.getId()))
    		.andExpect(status().is2xxSuccessful());
    }
    
    @Transactional
    @Rollback
    @Test
    void testWrappedResponse() throws Exception {
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
                .branch(Branch.USAF)
                .dodid("34567")
                .build();
                
                
    	personService.createPerson(person);
    	personService.createPerson(person1);
		
		PaginationWrappedResponse<List<PersonDto>> response = 
				PaginationWrappedResponse.<List<PersonDto>>builder()
				.data(Lists.newArrayList(person))
				.pagination(Pagination.builder()
						.page(0)
						.size(1)
						.totalElements(2L)
						.totalPages(2)
						.links(PaginationLink.builder()
								.next("http://localhost" + ENDPOINT_V2 + "?page=1&size=1")
								.last("http://localhost" + ENDPOINT_V2 + "?page=1&size=1")
								.build())
						.build())
				.build();
		

		mockMvc.perform(get(ENDPOINT_V2 + "?size=1"))
			.andExpect(status().isOk())
	        .andExpect(jsonPath("$.data", hasSize(1)))
	        .andExpect(jsonPath("$.pagination.page").value(response.getPagination().getPage()))
	        .andExpect(jsonPath("$.pagination.size").value(response.getPagination().getSize()))
	        .andExpect(jsonPath("$.pagination.totalElements").value(response.getPagination().getTotalElements()))
	        .andExpect(jsonPath("$.pagination.totalPages").value(response.getPagination().getTotalPages()))
	        .andExpect(jsonPath("$.pagination.links.next").value(response.getPagination().getLinks().getNext()))
	        .andExpect(jsonPath("$.pagination.links.last").value(response.getPagination().getLinks().getLast()));
    }
    
    @Transactional
    @Rollback
    @Test
    void testPersonPostFind() throws Exception {
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
                .branch(Branch.USAF)
                .dodid("34567")
                .build();
                
                
		mockMvc.perform(post(ENDPOINT_V2).contentType(MediaType.APPLICATION_JSON).content(OBJECT_MAPPER.writeValueAsString(person)));
		mockMvc.perform(post(ENDPOINT_V2).contentType(MediaType.APPLICATION_JSON).content(OBJECT_MAPPER.writeValueAsString(person1)));
		
		PersonFindDto personFindDto = PersonFindDto.builder()
				.findType(PersonFindType.EMAIL)
				.value(person.getEmail())
				.build();
		
		// Try to filter by email
		mockMvc.perform(post(ENDPOINT_V2 + "find")
				.content(OBJECT_MAPPER.writeValueAsString(personFindDto))
				.contentType(MediaType.APPLICATION_JSON)
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(person.getId().toString()));
		
		// Try to filter by dodid
		personFindDto.setFindType(PersonFindType.DODID);
		personFindDto.setValue(person1.getDodid());
		mockMvc.perform(post(ENDPOINT_V2 + "find")
				.content(OBJECT_MAPPER.writeValueAsString(personFindDto))
				.contentType(MediaType.APPLICATION_JSON)
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(person1.getId().toString()));

		
		// Try an invalid filter
		personFindDto.setFindType(null);
		mockMvc.perform(post(ENDPOINT_V2 + "find")
				.content(OBJECT_MAPPER.writeValueAsString(personFindDto))
				.contentType(MediaType.APPLICATION_JSON)
			)
			.andExpect(status().isBadRequest());
		
		mockMvc.perform(delete(ENDPOINT_V2 + "{id}", person.getId()));
		mockMvc.perform(delete(ENDPOINT_V2 + "{id}", person1.getId()));
    }
    
    @Transactional
    @Rollback
    @Test
    void testPersonPostFilter() throws Exception {
    	/**
    	 * Post everything to the database
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
    	
    	mockMvc.perform(post(ENDPOINT_V2).contentType(MediaType.APPLICATION_JSON).content(OBJECT_MAPPER.writeValueAsString(person)))
    		.andExpect(status().isCreated());
		mockMvc.perform(post(ENDPOINT_V2).contentType(MediaType.APPLICATION_JSON).content(OBJECT_MAPPER.writeValueAsString(person1)))
			.andExpect(status().isCreated());
		
		OrganizationDto orgWithPersonAsMember = OrganizationDto.builder()
				.id(UUID.randomUUID())
	            .name("TestOrg1")
	            .members(List.of(person.getId()))
	            .build();

        mockMvc.perform(post(ORGANIZATION)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(orgWithPersonAsMember)))
                .andExpect(status().isCreated());
        
        OrganizationDto orgWithPerson1AsLeader = OrganizationDto.builder()
        		.id(UUID.randomUUID())
	            .name("TestOrg2")
	            .leader(person1.getId())
	            .build();

        mockMvc.perform(post(ORGANIZATION)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(orgWithPerson1AsLeader)))
                .andExpect(status().isCreated());
		
        /**
         * Tests below here are for specific Person fields that will get transformed to join attributes
         * rank, organizationMemberships, organizationLeaderships, branch
         */
        
        // rank
        FilterDto filterDto = new FilterDto();
        FilterCriteria criteria = FilterCriteria.builder()
				.field("rank")
				.conditions(List.of(FilterCondition.builder()
										.operator(QueryOperator.EQUALS)
										.value("CIV")
										.build()))
				.build();
		filterDto.setFilterCriteria(Lists.newArrayList(criteria));
		
		mockMvc.perform(post(ENDPOINT_V2 + "/filter")
				.contentType(MediaType.APPLICATION_JSON)
				.content(OBJECT_MAPPER.writeValueAsString(filterDto)))
		.andExpect(status().isOk())
        .andExpect(jsonPath("$.data", hasSize(2)));
		
		// organizationMemberships
		criteria = FilterCriteria.builder()
				.field("organizationMemberships")
				.conditions(List.of(FilterCondition.builder()
										.operator(QueryOperator.EQUALS)
										.value(orgWithPersonAsMember.getId().toString())
										.build()))
				.build();
		filterDto.setFilterCriteria(Lists.newArrayList(criteria));
		
		mockMvc.perform(post(ENDPOINT_V2 + "/filter")
				.contentType(MediaType.APPLICATION_JSON)
				.content(OBJECT_MAPPER.writeValueAsString(filterDto)))
		.andExpect(status().isOk())
        .andExpect(jsonPath("$.data", hasSize(1)))
        .andExpect(jsonPath("$.data[0].id", is(person.getId().toString())));
		
		// organizationLeaderships
		criteria = FilterCriteria.builder()
				.field("organizationLeaderships")
				.conditions(List.of(FilterCondition.builder()
										.operator(QueryOperator.EQUALS)
										.value(orgWithPerson1AsLeader.getId().toString())
										.build()))
				.build();
		filterDto.setFilterCriteria(Lists.newArrayList(criteria));
		
		mockMvc.perform(post(ENDPOINT_V2 + "/filter")
				.contentType(MediaType.APPLICATION_JSON)
				.content(OBJECT_MAPPER.writeValueAsString(filterDto)))
		.andExpect(status().isOk())
        .andExpect(jsonPath("$.data", hasSize(1)))
        .andExpect(jsonPath("$.data[0].id", is(person1.getId().toString())));
		
		// branch
		criteria = FilterCriteria.builder()
				.field("branch")
				.conditions(List.of(FilterCondition.builder()
										.operator(QueryOperator.EQUALS)
										.value(person.getBranch().toString())
										.build()))
				.build();
		filterDto.setFilterCriteria(Lists.newArrayList(criteria));
		
		mockMvc.perform(post(ENDPOINT_V2 + "/filter")
				.contentType(MediaType.APPLICATION_JSON)
				.content(OBJECT_MAPPER.writeValueAsString(filterDto)))
		.andExpect(status().isOk())
        .andExpect(jsonPath("$.data", hasSize(1)))
        .andExpect(jsonPath("$.data[0].id", is(person.getId().toString())));
        
        /**
         * Tests below here are for testing the SpecificationBuilder
         */
	
		// try IN operator
		criteria = FilterCriteria.builder()
				.field("firstName")
				.conditions(List.of(FilterCondition.builder()
										.operator(QueryOperator.IN)
										.values(List.of("test", "1"))
										.build())
						)
				.build();
		filterDto.setFilterCriteria(Lists.newArrayList(criteria));
		
		mockMvc.perform(post(ENDPOINT_V2 + "/filter")
				.contentType(MediaType.APPLICATION_JSON)
				.content(OBJECT_MAPPER.writeValueAsString(filterDto)))
		.andExpect(status().isOk())
        .andExpect(jsonPath("$.data", hasSize(2)));
		
		// try LIKE
		criteria = FilterCriteria.builder()
				.field("firstName")
				.conditions(List.of(FilterCondition.builder()
										.operator(QueryOperator.LIKE)
										.value(person.getFirstName().substring(1))
										.build())
						)
				.build();
		filterDto.setFilterCriteria(Lists.newArrayList(criteria));
		
		mockMvc.perform(post(ENDPOINT_V2 + "/filter")
				.contentType(MediaType.APPLICATION_JSON)
				.content(OBJECT_MAPPER.writeValueAsString(filterDto)))
		.andExpect(status().isOk())
        .andExpect(jsonPath("$.data", hasSize(1)))
        .andExpect(jsonPath("$.data[0].id", is(person.getId().toString())));
		
		// try NOT_LIKE
		criteria = FilterCriteria.builder()
				.field("firstName")
				.conditions(List.of(FilterCondition.builder()
										.operator(QueryOperator.NOT_LIKE)
										.value(person.getFirstName().substring(1))
										.build())
						)
				.build();
		filterDto.setFilterCriteria(Lists.newArrayList(criteria));
		
		mockMvc.perform(post(ENDPOINT_V2 + "/filter")
				.contentType(MediaType.APPLICATION_JSON)
				.content(OBJECT_MAPPER.writeValueAsString(filterDto)))
		.andExpect(status().isOk())
        .andExpect(jsonPath("$.data", hasSize(1)))
        .andExpect(jsonPath("$.data[0].id", is(person1.getId().toString())));
		
		// try NOT_EQUALS
		criteria = FilterCriteria.builder()
				.field("firstName")
				.conditions(List.of(FilterCondition.builder()
										.operator(QueryOperator.NOT_EQUALS)
										.value(person.getFirstName())
										.build())
						)
				.build();
		filterDto.setFilterCriteria(Lists.newArrayList(criteria));
		
		mockMvc.perform(post(ENDPOINT_V2 + "/filter")
				.contentType(MediaType.APPLICATION_JSON)
				.content(OBJECT_MAPPER.writeValueAsString(filterDto)))
		.andExpect(status().isOk())
        .andExpect(jsonPath("$.data", hasSize(1)))
        .andExpect(jsonPath("$.data[0].id", is(person1.getId().toString())));
		
		// try EQUALS
		criteria = FilterCriteria.builder()
				.field("firstName")
				.conditions(List.of(FilterCondition.builder()
										.operator(QueryOperator.EQUALS)
										.value(person.getFirstName())
										.build())
						)
				.build();
		filterDto.setFilterCriteria(Lists.newArrayList(criteria));
		
		mockMvc.perform(post(ENDPOINT_V2 + "/filter")
				.contentType(MediaType.APPLICATION_JSON)
				.content(OBJECT_MAPPER.writeValueAsString(filterDto)))
		.andExpect(status().isOk())
        .andExpect(jsonPath("$.data", hasSize(1)))
        .andExpect(jsonPath("$.data[0].id", is(person.getId().toString())));
		
		// try ENDS_WITH & STARTS_WITH
		criteria = FilterCriteria.builder()
				.field("firstName")
				.conditions(List.of(FilterCondition.builder()
										.operator(QueryOperator.STARTS_WITH)
										.value("t")
										.build(),
									FilterCondition.builder()
										.operator(QueryOperator.ENDS_WITH)
										.value("t")
										.build())
						)
				.build();
		filterDto.setFilterCriteria(Lists.newArrayList(criteria));
		
		mockMvc.perform(post(ENDPOINT_V2 + "/filter")
				.contentType(MediaType.APPLICATION_JSON)
				.content(OBJECT_MAPPER.writeValueAsString(filterDto)))
		.andExpect(status().isOk())
        .andExpect(jsonPath("$.data", hasSize(1)))
        .andExpect(jsonPath("$.data[0].firstName", is(person.getFirstName())));
		
		// try multiple nested conditions
		criteria = FilterCriteria.builder()
				.field("firstName")
				.relationType(RelationType.OR)
				.conditions(List.of(FilterCondition.builder()
										.operator(QueryOperator.EQUALS)
										.value(person.getFirstName())
										.build(),
									FilterCondition.builder()
										.operator(QueryOperator.EQUALS)
										.value(person1.getFirstName())
										.build())
						)
				.build();
		filterDto.setFilterCriteria(Lists.newArrayList(criteria));
		
		mockMvc.perform(post(ENDPOINT_V2 + "/filter")
				.contentType(MediaType.APPLICATION_JSON)
				.content(OBJECT_MAPPER.writeValueAsString(filterDto)))
		.andExpect(status().isOk())
        .andExpect(jsonPath("$.data", hasSize(2)));

		// Try multiple filter criteria
		criteria = FilterCriteria.builder()
				.field("email")
				.conditions(List.of(FilterCondition.builder()
										.operator(QueryOperator.NOT_LIKE)
										.value(person1.getDodid())
										.build())
						)
				.build();
		
		FilterCriteria criteria1 = FilterCriteria.builder()
				.field("lastName")
				.conditions(List.of(FilterCondition.builder()
										.operator(QueryOperator.EQUALS)
										.value(person.getLastName())
										.build())
						)
				.build();
		filterDto.setFilterCriteria(Lists.newArrayList(criteria, criteria1));
		
		mockMvc.perform(post(ENDPOINT_V2 + "/filter")
				.contentType(MediaType.APPLICATION_JSON)
				.content(OBJECT_MAPPER.writeValueAsString(filterDto)))
		.andExpect(status().isOk())
        .andExpect(jsonPath("$.data", hasSize(1)))
        .andExpect(jsonPath("$.data[0].id", is(person.getId().toString())));
		
		// try with bad field
		criteria = FilterCriteria.builder()
				.field("doesNotExist")
				.conditions(List.of(FilterCondition.builder()
										.operator(QueryOperator.STARTS_WITH)
										.value("t")
										.build())
						)
				.build();
		filterDto.setFilterCriteria(Lists.newArrayList(criteria));
		
		mockMvc.perform(post(ENDPOINT_V2 + "/filter")
				.contentType(MediaType.APPLICATION_JSON)
				.content(OBJECT_MAPPER.writeValueAsString(filterDto)))
		.andExpect(status().isBadRequest());
		
		// try operator that does not support input type
		criteria = FilterCriteria.builder()
				.field("branch")
				.conditions(List.of(FilterCondition.builder()
										.operator(QueryOperator.LIKE)
										.value("CIV")
										.build())
						)
				.build();
		filterDto.setFilterCriteria(Lists.newArrayList(criteria));
		
		mockMvc.perform(post(ENDPOINT_V2 + "/filter")
				.contentType(MediaType.APPLICATION_JSON)
				.content(OBJECT_MAPPER.writeValueAsString(filterDto)))
		.andExpect(status().isBadRequest());
		
		// try failing cast to required type
		criteria = FilterCriteria.builder()
				.field("branch")
				.conditions(List.of(FilterCondition.builder()
										.operator(QueryOperator.EQUALS)
										.value("does not exist")
										.build())
						)
				.build();
		filterDto.setFilterCriteria(Lists.newArrayList(criteria));
		
		mockMvc.perform(post(ENDPOINT_V2 + "/filter")
				.contentType(MediaType.APPLICATION_JSON)
				.content(OBJECT_MAPPER.writeValueAsString(filterDto)))
		.andExpect(status().isBadRequest());
		
    }

    @Transactional
    @Rollback
    @Test
    void testP1JwtEndpoint() throws Exception {

        PlatformJwtDto dto = PlatformJwtDto
                .builder()
                .affiliation("US Air Force")
                .rank("E-9")
                .email("jimmy@test.com")
                .dodId("12345678")
                .familyName("smith")
                .givenName("jimmy")
                .build();

        // GO path - USAF
        mockMvc.perform(post("/v2/person/person-jwt")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rank", equalTo("CMSgt")))
                .andExpect(jsonPath("$.branch", equalTo("USAF")));

        // GO path - USA
        dto.setAffiliation("US Army");
        dto.setDodId("12345677");
        dto.setEmail("jimmy@army.com");
        dto.setRank("E-6");
        mockMvc.perform(post("/v2/person/person-jwt")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rank", equalTo("SSG")))
                .andExpect(jsonPath("$.branch", equalTo("USA")));

        // GO path - USSF
        dto.setAffiliation("US Space Force");
        dto.setDodId("12345676");
        dto.setEmail("jimmy@ussf.com");
        dto.setRank("E-3");
        mockMvc.perform(post("/v2/person/person-jwt")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rank", equalTo("Spc3")))
                .andExpect(jsonPath("$.branch", equalTo("USSF")));

        // GO path - USN
        dto.setAffiliation("US Navy");
        dto.setDodId("12345675");
        dto.setEmail("jimmy@navy.com");
        dto.setRank("O-2");
        mockMvc.perform(post("/v2/person/person-jwt")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rank", equalTo("LTJG")))
                .andExpect(jsonPath("$.branch", equalTo("USN")));

        // GO path - USCG
        dto.setAffiliation("US Coast Guard");
        dto.setDodId("12345674");
        dto.setEmail("jimmy@uscg.com");
        dto.setRank("O-2");
        mockMvc.perform(post("/v2/person/person-jwt")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rank", equalTo("LTJG")))
                .andExpect(jsonPath("$.branch", equalTo("USCG")));

        // GO path - USMC
        dto.setAffiliation("US Marine Corps");
        dto.setDodId("12345673");
        dto.setEmail("jimmy@usmc.com");
        dto.setRank("E-7");
        mockMvc.perform(post("/v2/person/person-jwt")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rank", equalTo("GySgt")))
                .andExpect(jsonPath("$.branch", equalTo("USMC")));

        // GO path - Contractor (it seems P1 has rank as N/A in the system for CTRs)
        dto.setAffiliation("Contractor");
        dto.setDodId("12345672");
        dto.setEmail("jimmy@revacomm.com");
        dto.setRank("N/A");
        mockMvc.perform(post("/v2/person/person-jwt")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rank", equalTo("CTR")))
                .andExpect(jsonPath("$.branch", equalTo("OTHER")));


        // ERROR path - defaults to "Unk" for rank and "OTHER" for branch
        dto.setEmail("jimmy2@test.com");
        dto.setDodId("123455555");
        dto.setRank(null);
        dto.setAffiliation(null);
        mockMvc.perform(post("/v2/person/person-jwt")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rank", equalTo("Unk")))
                .andExpect(jsonPath("$.branch", equalTo("OTHER")));
    }

    private static String resource(String name) throws IOException {
        return Resources.toString(Resources.getResource("integration/" + name), StandardCharsets.UTF_8);
    }
}
