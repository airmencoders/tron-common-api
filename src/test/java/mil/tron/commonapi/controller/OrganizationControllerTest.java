package mil.tron.commonapi.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.dto.OrganizationTerseDto;
import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.service.OrganizationService;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrganizationController.class)
public class OrganizationControllerTest {
	private static final String ENDPOINT = "/v1/organization/";
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Autowired
	private MockMvc mockMvc;
	
	@MockBean
	private OrganizationService organizationService;
	
	private Person testPerson;
	private Person testLeaderPerson;
	private Organization testOrg;
	private String testOrgJsonString;
	
	@BeforeEach
	public void beforeEachTest() throws JsonProcessingException {
		testPerson = new Person();
		testPerson.setFirstName("Test");
		testPerson.setLastName("Person");
		testPerson.setMiddleName("MVC");
		testPerson.setTitle("Person Title");
		testPerson.setEmail("test.person@mvc.com");
		
		testLeaderPerson = new Person();
		testLeaderPerson.setFirstName("Test");
		testLeaderPerson.setLastName("Person");
		testLeaderPerson.setMiddleName("Leader");
		testLeaderPerson.setTitle("Leader Person");
		testLeaderPerson.setEmail("test.leader@person.com");

		testOrg = new Organization();
		testOrg.setName("Test Org");
		testOrg.setLeader(testLeaderPerson);
		testOrg.addMember(testPerson);
		
		testOrgJsonString = OBJECT_MAPPER.writeValueAsString(testOrg);
	}
	
	@Nested
	class TestGet {

		@Test
		void testGetAll() throws Exception {
			List<Organization> orgs = new ArrayList<>();
			orgs.add(testOrg);

			Mockito.when(organizationService.getOrganizations()).thenReturn(orgs);
			
			mockMvc.perform(get(ENDPOINT))
				.andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(orgs)));
		}
		
		@Test
		void testGetById() throws Exception {
			Mockito.when(organizationService.getOrganization(Mockito.any(UUID.class))).thenReturn(testOrg);
			
			mockMvc.perform(get(ENDPOINT + "{id}", testOrg.getId()))
				.andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(testOrgJsonString));
		}
		
		@Test
		void testGetByIdNotFound() throws Exception {
			Mockito.when(organizationService.getOrganization(Mockito.any(UUID.class))).thenThrow(RecordNotFoundException.class);
			
			mockMvc.perform(get(ENDPOINT + "{id}", testOrg.getId()))
				.andExpect(status().isNotFound());
		}
		
		@Test
		void testGetByIdBadPathVariable() throws Exception {
			// Send an invalid UUID as ID path variable
			mockMvc.perform(get(ENDPOINT + "{id}", "asdf1234"))
				.andExpect(status().isBadRequest())
				.andExpect(result -> assertTrue(result.getResolvedException() instanceof MethodArgumentTypeMismatchException));
		}

		@Test
		void testGetOrganizationsTerse() throws Exception {
			ModelMapper mapper = new ModelMapper();
			OrganizationTerseDto dto = mapper.map(testOrg, OrganizationTerseDto.class);
			String dtoStr = OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(dto));
			Mockito.when(organizationService.getOrganizations()).thenReturn(Lists.newArrayList(testOrg));
			Mockito.when(organizationService.convertToDto(testOrg)).thenReturn(dto);
			mockMvc.perform(get(ENDPOINT + "?onlyIds=true"))
					.andExpect(status().isOk())
					.andExpect(result -> assertEquals(dtoStr, result.getResponse().getContentAsString()));
		}

		@Test
		void testGetOrganizationByIdTerse() throws Exception {
			ModelMapper mapper = new ModelMapper();
			OrganizationTerseDto dto = mapper.map(testOrg, OrganizationTerseDto.class);
			String dtoStr = OBJECT_MAPPER.writeValueAsString(dto);
			Mockito.when(organizationService.getOrganization(testOrg.getId())).thenReturn(testOrg);
			Mockito.when(organizationService.convertToDto(testOrg)).thenReturn(dto);
			mockMvc.perform(get(ENDPOINT + "/{id}?onlyIds=true", testOrg.getId()))
					.andExpect(status().isOk())
					.andExpect(result -> assertEquals(dtoStr, result.getResponse().getContentAsString()));
		}
	}
	
	@Nested
	class TestPost {
		@Test
		void testPostValidJsonBody() throws Exception {
			Mockito.when(organizationService.createOrganization(Mockito.any(Organization.class))).thenReturn(testOrg);
			
			mockMvc.perform(post(ENDPOINT)
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(testOrgJsonString))
				.andExpect(status().isCreated())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(testOrgJsonString));
		}
		
		@Test
		void testPostInvalidJsonBody() throws Exception {
			// Send empty string as bad json data
			mockMvc.perform(post(ENDPOINT).accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).content(""))
				.andExpect(status().isBadRequest())
				.andExpect(result -> assertTrue(result.getResolvedException() instanceof HttpMessageNotReadableException));
		}
		
		@Test
		void testPostOrganizationWithIdAlreadyExists() throws Exception {
			Mockito.when(organizationService.createOrganization(Mockito.any(Organization.class))).thenThrow(ResourceAlreadyExistsException.class);
			
			mockMvc.perform(post(ENDPOINT)
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(testOrgJsonString))
				.andExpect(status().isConflict());
		}

		@Test
		void testBulkCreate() throws Exception {
			List<Organization> newOrgs = Lists.newArrayList(
					new Organization(),
					new Organization(),
					new Organization(),
					new Organization()
			);

			Mockito.when(organizationService.bulkAddOrgs(Mockito.anyList())).then(returnsFirstArg());

			mockMvc.perform(post(ENDPOINT + "/organizations")
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(OBJECT_MAPPER.writeValueAsString(newOrgs)))
					.andExpect(status().isCreated())
					.andExpect(result -> assertEquals(OBJECT_MAPPER.writeValueAsString(newOrgs), result.getResponse().getContentAsString()));

		}
	}
	
	@Nested
	class TestPut {
		@Test
		void testPutValidJsonBody() throws Exception {
			Mockito.when(organizationService.updateOrganization(Mockito.any(UUID.class), Mockito.any(Organization.class))).thenReturn(testOrg);
			
			mockMvc.perform(put(ENDPOINT + "{id}", testOrg.getId())
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(testOrgJsonString))
				.andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(testOrgJsonString));
		}
		
		@Test
		void testPutInvalidJsonBody() throws Exception {
			// Send empty string as bad json data
			mockMvc.perform(put(ENDPOINT + "{id}", testOrg.getId())
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(""))
				.andExpect(status().isBadRequest())
				.andExpect(result -> assertTrue(result.getResolvedException() instanceof HttpMessageNotReadableException));
		}
		
		@Test
		void testPutInvalidBadPathVariable() throws Exception {
			// Send an invalid UUID as ID path variable
			mockMvc.perform(put(ENDPOINT + "{id}", "asdf1234"))
				.andExpect(status().isBadRequest())
				.andExpect(result -> assertTrue(result.getResolvedException() instanceof MethodArgumentTypeMismatchException));
		}
		
		@Test
		void testPutResourceDoesNotExist() throws Exception {
			Mockito.when(organizationService.updateOrganization(Mockito.any(UUID.class), Mockito.any(Organization.class))).thenReturn(null);
			
			mockMvc.perform(put(ENDPOINT + "{id}", testOrg.getId())
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(testOrgJsonString))
				.andExpect(status().isNotFound());
		}
	}
	
	@Nested
	class TestDelete {
		@Test
		void testDelete() throws Exception {
			mockMvc.perform(delete(ENDPOINT + "{id}", testOrg.getId()))
				.andExpect(status().isNoContent());
		}
		
		@Test
		void testDeleteBadPathVariable() throws Exception {
			mockMvc.perform(delete(ENDPOINT + "{id}", "asdf1234"))
				.andExpect(status().isBadRequest())
				.andExpect(result -> assertTrue(result.getResolvedException() instanceof MethodArgumentTypeMismatchException));
		}
	}

	@Nested
	class TestPatch {
		@Test
		void testChangeName() throws Exception {
			Map<String, String> attribs = new HashMap<>();
			attribs.put("name", "test org");
			Organization newOrg = new Organization();
			newOrg.setId(testOrg.getId());
			newOrg.setName("test org");

			Mockito.when(organizationService.modifyAttributes(Mockito.any(UUID.class), Mockito.any(Map.class))).thenReturn(newOrg);
			MvcResult result = mockMvc.perform(patch(ENDPOINT + "{id}", testOrg.getId())
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(OBJECT_MAPPER.writeValueAsString(attribs)))
					.andExpect(status().isOk())
					.andReturn();

			assertEquals("test org", OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), Organization.class).getName());
		}

		@Test
		void testAddRemoveMember() throws Exception {
			Person p = new Person();

			Organization newOrg = new Organization();
			newOrg.setId(testOrg.getId());
			newOrg.setName("test org");
			newOrg.addMember(p);

			Mockito.when(organizationService.addOrganizationMember(Mockito.any(UUID.class), Mockito.any(List.class))).thenReturn(newOrg);
			MvcResult result = mockMvc.perform(patch(ENDPOINT + "{id}/members", testOrg.getId())
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(p.getId()))))
					.andExpect(status().isOk())
					.andReturn();

			// test it "added" to org
			assertEquals(1, OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), Organization.class).getMembers().size());

			newOrg.removeMember(p);
			Mockito.when(organizationService.removeOrganizationMember(Mockito.any(UUID.class), Mockito.any(List.class))).thenReturn(newOrg);
			MvcResult result2 = mockMvc.perform(patch(ENDPOINT + "{id}/members", testOrg.getId())
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(p.getId()))))
					.andExpect(status().isOk())
					.andReturn();

			// test it "removed" from org
			assertEquals(0, OBJECT_MAPPER.readValue(result2.getResponse().getContentAsString(), Organization.class).getMembers().size());
		}
	}
}
