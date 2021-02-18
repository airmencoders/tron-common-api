package mil.tron.commonapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import mil.tron.commonapi.dto.AppClientUserDto;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.service.AppClientUserPreAuthenticatedService;
import mil.tron.commonapi.service.AppClientUserService;

@WebMvcTest(AppClientController.class)
class AppClientControllerTest {

	private static final String ENDPOINT = "/v1/app-client/";
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	
	@Autowired
	private MockMvc mockMvc;
	
	@MockBean
	private AppClientUserPreAuthenticatedService appClientUserPreAuthenticatedService;
	
	@MockBean
	private AppClientUserService userService;
	
	private List<AppClientUserDto> users;
	private AppClientUserDto userDto;
	
	@BeforeEach
	void setup() {
		users = new ArrayList<>();
		
		userDto = new AppClientUserDto();
		userDto.setId(UUID.randomUUID());
		userDto.setName("User A");
		userDto.setPrivileges(new ArrayList<Privilege>());
		
		users.add(userDto);
	}
	
	@Nested 
	class TestGet {
		@Test
		void getAll() throws Exception {
			Mockito.when(userService.getAppClientUsers()).thenReturn(users);
			
			mockMvc.perform(get(ENDPOINT))
				.andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(users)));
		}	
	}
	
	@Nested
	class TestPost {
		@Test
		void conflict() throws Exception {
			Mockito.when(userService.createAppClientUser(Mockito.any())).thenThrow(ResourceAlreadyExistsException.class);
			
			mockMvc.perform(post(ENDPOINT)
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(OBJECT_MAPPER.writeValueAsString(userDto)))
				.andExpect(status().isConflict())
				.andExpect(result -> assertTrue(result.getResolvedException() instanceof ResourceAlreadyExistsException));
		}
		
		@Test
		void badRequest() throws Exception {
			mockMvc.perform(post(ENDPOINT)
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content("as"))
				.andExpect(status().isBadRequest())
				.andExpect(result -> assertTrue(result.getResolvedException() instanceof HttpMessageNotReadableException));
		}
		
		@Test
		void success() throws Exception {
			Mockito.when(userService.createAppClientUser(Mockito.any(AppClientUserDto.class))).thenReturn(userDto);
			
			mockMvc.perform(post(ENDPOINT)
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(OBJECT_MAPPER.writeValueAsString(userDto)))
				.andExpect(status().isCreated())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(userDto)));
		}
	}
	
	@Nested
	class TestPut {
		@Test
		void success() throws Exception {
			Mockito.when(userService.updateAppClientUser(Mockito.any(UUID.class), Mockito.any(AppClientUserDto.class))).thenReturn(userDto);
			
			mockMvc.perform(put(ENDPOINT + "{id}", userDto.getId())
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(OBJECT_MAPPER.writeValueAsString(userDto)))
				.andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(userDto)));
		}
		
		@Test
		void badRequest() throws Exception {
			mockMvc.perform(put(ENDPOINT + "{id}", userDto.getId())
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content("asdf"))
				.andExpect(status().isBadRequest())
				.andExpect(result -> assertTrue(result.getResolvedException() instanceof HttpMessageNotReadableException));
		}
		
		@Test
		void badPathVariable() throws Exception {
			mockMvc.perform(put(ENDPOINT + "{id}", "asdf1234"))
				.andExpect(status().isBadRequest())
				.andExpect(result -> assertTrue(result.getResolvedException() instanceof MethodArgumentTypeMismatchException));
		}
		
		@Test
		void resourceDoesNotExist() throws Exception {
			Mockito.when(userService.updateAppClientUser(Mockito.any(UUID.class), Mockito.any(AppClientUserDto.class))).thenThrow(RecordNotFoundException.class);

			mockMvc.perform(put(ENDPOINT + "{id}", UUID.randomUUID())
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(OBJECT_MAPPER.writeValueAsString(userDto)))
				.andExpect(status().isNotFound());
		}
	}
}
