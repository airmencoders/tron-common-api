package mil.tron.commonapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import mil.tron.commonapi.organization.Organization;
import mil.tron.commonapi.person.Person;
import mil.tron.commonapi.service.OrganizationService;

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
			Mockito.when(organizationService.getOrganization(Mockito.any(UUID.class))).thenReturn(null);
			
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
			Mockito.when(organizationService.createOrganization(Mockito.any(Organization.class))).thenReturn(null);
			
			mockMvc.perform(post(ENDPOINT)
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(testOrgJsonString))
				.andExpect(status().isBadRequest());
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
}
