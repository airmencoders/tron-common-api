package mil.tron.commonapi.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;

import mil.tron.commonapi.dto.OrganizationDto;
import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.orgtypes.Unit;

import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.*;

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
@TestPropertySource(locations = "classpath:application-test.properties")
@AutoConfigureMockMvc
public class PersonIntegrationTest {
    private static final String ENDPOINT = "/v1/person/";
    private static final String ORGANIZATION = "/v1/organization/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

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

        mockMvc.perform(post(ENDPOINT + "persons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(newPeople)))
                .andExpect(status().isCreated())
                .andExpect(result -> assertEquals(2, OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), PersonDto[].class).length));

        PersonDto a3 = new PersonDto();
        a3.setEmail("test1@test.com");
        a3.setTitle("SSGT");
        a3.setRank("SSgt");
        a3.setBranch(Branch.USAF);

        // test that we can't add someone with a dup email
        mockMvc.perform(post(ENDPOINT + "persons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(a3))))
                .andExpect(status().isConflict());

        // make sure the entities we added were actually created
        mockMvc.perform(get(ENDPOINT + "/" + person.getId()))
            .andExpect(status().isOk());
        mockMvc.perform(get(ENDPOINT + "/" + a2.getId()))
            .andExpect(status().isOk());
            
        // test pagination
        mockMvc.perform(get(ENDPOINT + "?page=1&limit=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get(ENDPOINT + "?page=2&limit=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }
    
    @Transactional
    @Rollback
    @Test
    void testPersonFilter() throws Exception {
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
		mockMvc.perform(get(ENDPOINT + String.format("filter/?filterType=email&filterValue=%s", person.getEmail())))
			.andExpect(status().isOk())
			.andExpect(result -> assertThat(result.getResponse().getContentAsString())
					.isEqualTo(OBJECT_MAPPER.writeValueAsString(person)));
		
		// Try to filter by dodid
		mockMvc.perform(get(ENDPOINT + String.format("filter/?filterType=dodid&filterValue=%s", person1.getDodid())))
			.andExpect(status().isOk())
			.andExpect(result -> assertThat(result.getResponse().getContentAsString())
					.isEqualTo(OBJECT_MAPPER.writeValueAsString(person1)));
		
		// Try an invalid filter
		mockMvc.perform(get(ENDPOINT + String.format("filter/?filterType=asdf&filterValue=%s", person1.getDodid())))
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

    @Test
    public void testAirmanMetadata() throws Exception {
        String id = OBJECT_MAPPER.readTree(
                mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(resource("newAirman.json")))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("afsc").value("11FX"))
                        .andExpect(jsonPath("imds").value("sucks"))
                        .andReturn().getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get(ENDPOINT + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("afsc").value("11FX"))
                .andExpect(jsonPath("imds").value("sucks"));

        mockMvc.perform(put(ENDPOINT + id).contentType(MediaType.APPLICATION_JSON).content(resource("updatedAirman.json")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("imds").doesNotExist())
                .andExpect(jsonPath("go81").value("tperson"))
                .andExpect(jsonPath("afsc").value("99A"));

        mockMvc.perform(get(ENDPOINT + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("imds").doesNotExist())
                .andExpect(jsonPath("go81").value("tperson"))
                .andExpect(jsonPath("afsc").value("99A"));
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

    private static String resource(String name) throws IOException {
        return Resources.toString(Resources.getResource("integration/" + name), StandardCharsets.UTF_8);
    }
}
