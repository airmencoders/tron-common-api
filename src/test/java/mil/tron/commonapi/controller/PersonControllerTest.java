package mil.tron.commonapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import mil.tron.commonapi.person.Person;
import mil.tron.commonapi.service.PersonServiceImpl;

@WebMvcTest(PersonController.class)
public class PersonControllerTest {
	@Autowired
	private MockMvc mockMvc;
	
	@MockBean
	private PersonServiceImpl personService;
	
	private static final ObjectMapper objectMapper = new ObjectMapper();
	private Person testPerson;
	
	@BeforeEach
	public void beforeEachTest() {
		testPerson = new Person();
		testPerson.setFirstName("Test");
		testPerson.setLastName("Person");
		testPerson.setMiddleName("MVC");
		testPerson.setTitle("Person Title");
		testPerson.setEmail("test.person@mvc.com");
	}

	@Nested
	class TestGet {
		@Test
		public void testGet() throws Exception {
			List<Person> persons = new ArrayList<>();
			persons.add(testPerson);

			Mockito.when(personService.getPersons()).thenReturn(persons);
			
			mockMvc.perform(get("/person"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", Matchers.hasSize(1)))
				.andExpect(jsonPath("$[0].firstName").value(testPerson.getFirstName()))
				.andExpect(jsonPath("$[0].lastName").value(testPerson.getLastName()))
				.andExpect(jsonPath("$[0].middleName").value(testPerson.getMiddleName()))
				.andExpect(jsonPath("$[0].title").value(testPerson.getTitle()))
				.andExpect(jsonPath("$[0].email").value(testPerson.getEmail()));
		}
		
		@Test
		public void testGetById() throws Exception {
			Mockito.when(personService.getPerson(Mockito.any(UUID.class))).thenReturn(testPerson);
			
			mockMvc.perform(get(String.format("/person/%s", testPerson.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.firstName").value(testPerson.getFirstName()))
				.andExpect(jsonPath("$.lastName").value(testPerson.getLastName()))
				.andExpect(jsonPath("$.middleName").value(testPerson.getMiddleName()))
				.andExpect(jsonPath("$.title").value(testPerson.getTitle()))
				.andExpect(jsonPath("$.email").value(testPerson.getEmail()));
		}
	}
	
	@Test
	public void testPost() throws Exception {
		Mockito.when(personService.createPerson(Mockito.any(Person.class))).thenReturn(testPerson);
		
		String jsonPerson = objectMapper.writeValueAsString(testPerson);
		
		mockMvc.perform(post("/person").accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).content(jsonPerson))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.firstName").value(testPerson.getFirstName()))
			.andExpect(jsonPath("$.lastName").value(testPerson.getLastName()))
			.andExpect(jsonPath("$.middleName").value(testPerson.getMiddleName()))
			.andExpect(jsonPath("$.title").value(testPerson.getTitle()))
			.andExpect(jsonPath("$.email").value(testPerson.getEmail()));
	}
	
	@Test
	public void testPut() throws Exception {
		Mockito.when(personService.updatePerson(Mockito.any(UUID.class), Mockito.any(Person.class))).thenReturn(testPerson);
		
		String jsonPerson = objectMapper.writeValueAsString(testPerson);
		
		mockMvc.perform(put(String.format("/person/%s", testPerson.getId())).accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).content(jsonPerson))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.firstName").value(testPerson.getFirstName()))
			.andExpect(jsonPath("$.lastName").value(testPerson.getLastName()))
			.andExpect(jsonPath("$.middleName").value(testPerson.getMiddleName()))
			.andExpect(jsonPath("$.title").value(testPerson.getTitle()))
			.andExpect(jsonPath("$.email").value(testPerson.getEmail()));
	}
	
	@Test
	public void testDelete() throws Exception {
		Mockito.doNothing().when(personService).deletePerson(testPerson.getId());
		
		mockMvc.perform(delete(String.format("/person/%s", testPerson.getId())))
			.andExpect(status().isOk());
	}
}
