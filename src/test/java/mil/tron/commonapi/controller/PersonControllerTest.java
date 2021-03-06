package mil.tron.commonapi.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.service.AppClientUserPreAuthenticatedService;
import mil.tron.commonapi.service.PersonConversionOptions;
import mil.tron.commonapi.service.PersonService;
import org.assertj.core.util.Lists;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
public class PersonControllerTest {
	private static final String ENDPOINT = "/v1/person/";
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	
	@Autowired
	private MockMvc mockMvc;
	
	@MockBean
	private PersonService personService;

	private PersonDto testPerson;
	private String testPersonJson;
	
	@BeforeEach
	public void beforeEachTest() throws JsonProcessingException {
		testPerson = new PersonDto();
		testPerson.setFirstName("Test");
		testPerson.setLastName("Person");
		testPerson.setMiddleName("MVC");
		testPerson.setTitle("Person Title");
		testPerson.setEmail("test.person@mvc.com");
		
		testPersonJson = OBJECT_MAPPER.writeValueAsString(testPerson);
	}

	@Nested
	class TestGet {
		@Test
		void testGetAll() throws Exception {
			List<PersonDto> persons = new ArrayList<>();
			persons.add(testPerson);

			Mockito.when(personService.getPersons(Mockito.any())).thenReturn(persons);
			
			mockMvc.perform(get(ENDPOINT))
				.andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(persons)));
		}

		@Test
		void testGetById() throws Exception {
			Mockito.when(personService.getPersonDto(Mockito.any(UUID.class), Mockito.any())).thenReturn(testPerson);

			mockMvc.perform(get(ENDPOINT + "{id}", testPerson.getId()))
				.andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(testPersonJson));
		}
		
		@Test
		void testGetByIdBadPathVariable() throws Exception {
			// Send an invalid UUID as ID path variable
			mockMvc.perform(get(ENDPOINT + "{id}", "asdf1234"))
				.andExpect(status().isBadRequest())
				.andExpect(result -> assertTrue(result.getResolvedException() instanceof MethodArgumentTypeMismatchException));
		}

        @Captor ArgumentCaptor<PersonConversionOptions> optionsCaptor;

        @Test
        void testGetWithMemberships() throws Exception {
            mockMvc.perform(get(ENDPOINT + "?memberships=true"))
                .andExpect(status().isOk());

            Mockito.verify(personService).getPersons(optionsCaptor.capture());
            assertEquals(true, optionsCaptor.getValue().isMembershipsIncluded());
            assertEquals(false, optionsCaptor.getValue().isLeadershipsIncluded());
        }

        @Test
        void testGetWithLeaderships() throws Exception {
            mockMvc.perform(get(ENDPOINT + "?leaderships=true"))
                .andExpect(status().isOk());

            Mockito.verify(personService).getPersons(optionsCaptor.capture());
            assertEquals(false, optionsCaptor.getValue().isMembershipsIncluded());
            assertEquals(true, optionsCaptor.getValue().isLeadershipsIncluded());
        }

        @Test
        void testGetWithMembershipsAndLeaderships() throws Exception {
            mockMvc.perform(get(ENDPOINT + "?memberships=true&leaderships=true"))
                .andExpect(status().isOk());

            Mockito.verify(personService).getPersons(optionsCaptor.capture());
            assertEquals(true, optionsCaptor.getValue().isMembershipsIncluded());
            assertEquals(true, optionsCaptor.getValue().isLeadershipsIncluded());
        }
	}
	
	@Nested
	class TestPost {
		@Test
		void testPostValidJsonBody() throws Exception {
			Mockito.when(personService.createPerson(Mockito.any(PersonDto.class))).thenReturn(testPerson);
			
			mockMvc.perform(post(ENDPOINT)
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(testPersonJson))
				.andExpect(status().isCreated())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(testPersonJson));
		}
		
		@Test
		void testPostInvalidJsonBody() throws Exception {
			// Send empty string as bad json data
			mockMvc.perform(post(ENDPOINT)
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(""))
				.andExpect(status().isBadRequest())
				.andExpect(result -> assertTrue(result.getResolvedException() instanceof HttpMessageNotReadableException));
		}

		@Test
		void testBulkCreate() throws Exception {
			List<PersonDto> people = Lists.newArrayList(
					new PersonDto(),
					new PersonDto(),
					new PersonDto(),
					new PersonDto()
			);

			Mockito.when(personService.bulkAddPeople(Mockito.anyList())).then(returnsFirstArg());

			mockMvc.perform(post(ENDPOINT + "/persons")
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(OBJECT_MAPPER.writeValueAsString(people)))
					.andExpect(status().isCreated())
					.andExpect(result -> assertEquals(OBJECT_MAPPER.writeValueAsString(people), result.getResponse().getContentAsString()));

		}
	}
	
	@Nested
	class TestPut {
		@Test
		void testPutValidJsonBody() throws Exception {
			Mockito.when(personService.updatePerson(Mockito.any(UUID.class), Mockito.any(PersonDto.class))).thenReturn(testPerson);
			
			mockMvc.perform(put(ENDPOINT + "{id}", testPerson.getId())
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(testPersonJson))
				.andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(testPersonJson));
		}
		
		@Test
		void testPutInvalidJsonBody() throws Exception {
			// Send empty string as bad json data
			mockMvc.perform(put(ENDPOINT + "{id}", testPerson.getId())
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
			Mockito.when(personService.updatePerson(Mockito.any(UUID.class), Mockito.any(PersonDto.class))).thenThrow(new RecordNotFoundException("Record not found"));
			
			mockMvc.perform(put(ENDPOINT + "{id}", testPerson.getId())
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(testPersonJson))
				.andExpect(status().isNotFound());
		}
	}

	@Nested
	class TestPatch {
		@Test
		void testPatchValidJsonBody() throws Exception {
			Mockito.when(personService.patchPerson(Mockito.any(UUID.class), Mockito.any(JsonPatch.class))).thenReturn(testPerson);

			JSONObject content = new JSONObject();
			content.put("op","replace");
			content.put("path","/firstName");
			content.put("value",testPerson.getFirstName());
			JSONArray patch = new JSONArray();
			patch.put(content);

			mockMvc.perform(patch(ENDPOINT + "{id}", testPerson.getId())
						.contentType("application/json-patch+json")
						.accept(MediaType.APPLICATION_JSON)
						.content(patch.toString()))
					.andExpect(status().isOk())
					.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(testPersonJson));
		}

//		@Test
//		void testPatchInvalidJsonBody() throws Exception {
//			Mockito.when(personService.patchPerson(Mockito.any(UUID.class), Mockito.any(JsonPatch.class))).thenReturn(testPerson);
//			// Send empty string as bad json data
//			mockMvc.perform(patch(ENDPOINT + "{id}", testPerson.getId())
//						.contentType("application/json-patch+json")
//						.accept(MediaType.APPLICATION_JSON)
//						.content(""));
//					.andDo(MockMvcResultHandlers.print());
//					.andExpect(status().isBadRequest());
//					.andExpect(result -> assertTrue(result.getResolvedException() instanceof HttpMessageNotReadableException));
//		}
//
//		@Test
//		void testPatchInvalidBadPathVariable() throws Exception {
//			// Send an invalid UUID as ID path variable
//			mockMvc.perform(patch(ENDPOINT + "{id}", "asdf1234"))
//					.andExpect(status().isBadRequest())
//					.andExpect(result -> assertTrue(result.getResolvedException() instanceof MethodArgumentTypeMismatchException));
//		}
//
		@Test
		void testPatchResourceDoesNotExist() throws Exception {
			Mockito.when(personService.patchPerson(Mockito.any(UUID.class), Mockito.any(JsonPatch.class))).thenThrow(new RecordNotFoundException("Record not found"));

			JSONObject content = new JSONObject();
			content.put("op","replace");
			content.put("path","/firstName");
			content.put("value",testPerson.getFirstName());
			JSONArray patch = new JSONArray();
			patch.put(content);

			mockMvc.perform(put(ENDPOINT + "{id}", testPerson.getId())
						.contentType("application/json-patch+json")
						.accept(MediaType.APPLICATION_JSON)
						.content(patch.toString()))
					.andExpect(status().isNotFound());
		}

	}
	
	@Nested
	class TestDelete {
		@Test
		void testDelete() throws Exception {
			Mockito.doNothing().when(personService).deletePerson(testPerson.getId());
			
			mockMvc.perform(delete(ENDPOINT + "{id}", testPerson.getId()))
				.andExpect(status().isNoContent());
		}
		
		@Test
		void testDeleteBadPathVariable() throws Exception {
			Mockito.doNothing().when(personService).deletePerson(testPerson.getId());
			
			mockMvc.perform(delete(ENDPOINT + "{id}", "asdf1234"))
				.andExpect(status().isBadRequest())
				.andExpect(result -> assertTrue(result.getResolvedException() instanceof MethodArgumentTypeMismatchException));
		}
	}
}
