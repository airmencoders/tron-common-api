package mil.tron.commonapi.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import mil.tron.commonapi.person.Person;
import mil.tron.commonapi.service.PersonServiceImpl;

@WebMvcTest(PersonController.class)
public class PersonControllerTest {
	@Autowired
	private MockMvc mockMvc;
	
	@MockBean
	private PersonServiceImpl personService;
	
	private static final String ENDPOINT = "/person/";
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final String MALFORMED_JSON = "{\r\n"
			+ "  \"firstName\": \"Test\",\r\n"
			+ "  \"middleName\": \"This\",\r\n"
			+ "  \"lastName\": \"Person\",\r\n"
			+ "  \"title\": \"Some title\"\r\n"
			+ "  \"email\": \"test.this.person.@sometitle.com\"\r\n"
			+ "}";
	private Person testPerson;
	private String jsonPerson;
	
	@BeforeEach
	public void beforeEachTest() throws JsonProcessingException {
		testPerson = new Person();
		testPerson.setFirstName("Test");
		testPerson.setLastName("Person");
		testPerson.setMiddleName("MVC");
		testPerson.setTitle("Person Title");
		testPerson.setEmail("test.person@mvc.com");
		
		jsonPerson = OBJECT_MAPPER.writeValueAsString(testPerson);
	}

	@Nested
	class TestGet {
		@Test
		public void testGetAll() throws Exception {
			List<Person> persons = new ArrayList<>();
			persons.add(testPerson);

			Mockito.when(personService.getPersons()).thenReturn(persons);
			
			mockMvc.perform(get(ENDPOINT))
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
			
			mockMvc.perform(get(ENDPOINT + "{id}", testPerson.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.firstName").value(testPerson.getFirstName()))
				.andExpect(jsonPath("$.lastName").value(testPerson.getLastName()))
				.andExpect(jsonPath("$.middleName").value(testPerson.getMiddleName()))
				.andExpect(jsonPath("$.title").value(testPerson.getTitle()))
				.andExpect(jsonPath("$.email").value(testPerson.getEmail()));
		}
		
		@Test
		public void testGetByIdBadPathVariable() throws Exception {
			// Send an invalid UUID as ID path variable
			mockMvc.perform(get(ENDPOINT + "{id}", "asdf1234"))
				.andExpect(status().isBadRequest())
				.andExpect(result -> assertTrue(result.getResolvedException() instanceof MethodArgumentTypeMismatchException));
		}
	}
	
	@Nested
	class TestPost {
		@Test
		public void testPostValidJsonBody() throws Exception {
			Mockito.when(personService.createPerson(Mockito.any(Person.class))).thenReturn(testPerson);
			
			mockMvc.perform(post(ENDPOINT).accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).content(jsonPerson))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.firstName").value(testPerson.getFirstName()))
				.andExpect(jsonPath("$.lastName").value(testPerson.getLastName()))
				.andExpect(jsonPath("$.middleName").value(testPerson.getMiddleName()))
				.andExpect(jsonPath("$.title").value(testPerson.getTitle()))
				.andExpect(jsonPath("$.email").value(testPerson.getEmail()));
		}
		
		@Test
		public void testPostInvalidJsonBody() throws Exception {
			// Send malformed json data
			mockMvc.perform(post(ENDPOINT).accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).content(MALFORMED_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(result -> assertTrue(result.getResolvedException() instanceof HttpMessageNotReadableException));
		}
	}
	
	@Nested
	class TestPut {
		@Test
		public void testPutValidJsonBody() throws Exception {
			Mockito.when(personService.updatePerson(Mockito.any(UUID.class), Mockito.any(Person.class))).thenReturn(testPerson);
			
			mockMvc.perform(put(ENDPOINT + "{id}", testPerson.getId()).accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).content(jsonPerson))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.firstName").value(testPerson.getFirstName()))
				.andExpect(jsonPath("$.lastName").value(testPerson.getLastName()))
				.andExpect(jsonPath("$.middleName").value(testPerson.getMiddleName()))
				.andExpect(jsonPath("$.title").value(testPerson.getTitle()))
				.andExpect(jsonPath("$.email").value(testPerson.getEmail()));
		}
		
		@Test
		public void testPutInvalidJsonBody() throws Exception {
			// Send malformed json data
			mockMvc.perform(put(ENDPOINT + "{id}", testPerson.getId()).accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).content(MALFORMED_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(result -> assertTrue(result.getResolvedException() instanceof HttpMessageNotReadableException));
		}
		
		@Test
		public void testPutInvalidBadPathVariable() throws Exception {
			// Send an invalid UUID as ID path variable
			mockMvc.perform(put(ENDPOINT + "{id}", "asdf1234"))
				.andExpect(status().isBadRequest())
				.andExpect(result -> assertTrue(result.getResolvedException() instanceof MethodArgumentTypeMismatchException));
		}
		
		@Test
		public void testPutResourceDoesNotExist() throws Exception {
			Mockito.when(personService.updatePerson(Mockito.any(UUID.class), Mockito.any(Person.class))).thenReturn(null);
			
			mockMvc.perform(put(ENDPOINT + "{id}", testPerson.getId()).accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).content(jsonPerson))
				.andExpect(status().isNotFound());
		}
	}
	
	@Nested
	class TestDelete {
		@Test
		public void testDelete() throws Exception {
			Mockito.doNothing().when(personService).deletePerson(testPerson.getId());
			
			mockMvc.perform(delete(ENDPOINT + "{id}", testPerson.getId()))
				.andExpect(status().isNoContent());
		}
		
		@Test
		public void testDeleteBadPathVariable() throws Exception {
			Mockito.doNothing().when(personService).deletePerson(testPerson.getId());
			
			mockMvc.perform(delete(ENDPOINT + "{id}", "asdf1234"))
				.andExpect(status().isBadRequest())
				.andExpect(result -> assertTrue(result.getResolvedException() instanceof MethodArgumentTypeMismatchException));
		}
	}
}
