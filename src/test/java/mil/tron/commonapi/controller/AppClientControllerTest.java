package mil.tron.commonapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.dto.PrivilegeDto;
import mil.tron.commonapi.dto.appclient.AppClientUserDetailsDto;
import mil.tron.commonapi.dto.appclient.AppClientUserDto;
import mil.tron.commonapi.dto.response.WrappedResponse;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.service.AppClientUserPreAuthenticatedService;
import mil.tron.commonapi.service.AppClientUserService;
import mil.tron.commonapi.service.PrivilegeService;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AppClientControllerTest {

	private static final String ENDPOINT = "/v1/app-client/";
	private static final String ENDPOINT_V2 = "/v2/app-client/";
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	
	@Autowired
	private MockMvc mockMvc;
	
	@MockBean
	private AppClientUserPreAuthenticatedService appClientUserPreAuthenticatedService;
	
	@MockBean
	private AppClientUserService userService;

	@MockBean
	private PrivilegeService privService;
	
	private List<AppClientUserDto> users;
	private AppClientUserDto userDto;

	
	@BeforeEach
	void setup() {

		users = new ArrayList<>();
		
		userDto = new AppClientUserDto();
		userDto.setId(UUID.randomUUID());
		userDto.setName("User A");
		userDto.setPrivileges(new ArrayList<>());
		
		users.add(userDto);
	}
	
	@Nested 
	class TestGet {

		@Test
		@WithMockUser(username = "DashboardUser", authorities = { "DASHBOARD_ADMIN", "DASHBOARD_USER" })
		void getAll() throws Exception {
			Mockito.when(userService.getAppClientUsers()).thenReturn(users);
			
			mockMvc.perform(get(ENDPOINT))
				.andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(users)));
			
			// V2
			WrappedResponse<List<AppClientUserDto>> wrappedResponse = WrappedResponse.<List<AppClientUserDto>>builder().data(users).build();
			mockMvc.perform(get(ENDPOINT_V2))
				.andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(wrappedResponse)));
		}

		@Test
		@WithMockUser(username = "DashboardUser", authorities = { "DASHBOARD_ADMIN", "DASHBOARD_USER" })
		void getById() throws Exception {
			Mockito.when(userService.getAppClient(Mockito.any(UUID.class)))
					.thenReturn(AppClientUserDetailsDto.builder()
							.id(users.get(0).getId())
							.build());

			mockMvc.perform(get(ENDPOINT + "{id}", users.get(0).getId()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.id", equalTo(users.get(0).getId().toString())));
		}

		@Test
		void getByIdFails() throws Exception {

			mockMvc.perform(get(ENDPOINT + "{id}", users.get(0).getId()))
					.andExpect(status().isForbidden());
		}

		@Test
		void testGetAllFailsAsNoAdmin() throws Exception {
			Mockito.when(userService.getAppClientUsers()).thenReturn(users);

			mockMvc.perform(get(ENDPOINT))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$", hasSize(0)));
		}

		@Test
		@WithMockUser(username = "DashboardUser", authorities = { "APP_CLIENT_USER" })
		void testGetClientRelatedPrivs() throws Exception {
			PrivilegeDto dto1 = new PrivilegeDto();
			dto1.setId(1L);
			dto1.setName("WRITE");

			PrivilegeDto dto2 = new PrivilegeDto();
			dto2.setId(2L);
			dto2.setName("READ");

			PrivilegeDto dto3 = new PrivilegeDto();
			dto3.setId(3L);
			dto3.setName("SCRATCH_WRITE");

			PrivilegeDto dto4 = new PrivilegeDto();
			dto4.setId(4L);
			dto4.setName("APP_CLIENT_DEVELOPER");

			Mockito.when(privService.getPrivileges())
					.thenReturn(Lists.newArrayList(dto1, dto2, dto3, dto4));

			mockMvc.perform(get(ENDPOINT + "privs"))
					.andExpect(status().isOk())
					.andExpect(MockMvcResultMatchers.jsonPath("$", hasSize(3)));
			
			// V2
			mockMvc.perform(get(ENDPOINT_V2 + "privs"))
				.andExpect(status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.data", hasSize(3)));
		}
	}
	
	@Nested
	@WithMockUser(username = "DashboardUser", authorities = { "DASHBOARD_ADMIN", "DASHBOARD_USER" })
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
	@WithMockUser(username = "DashboardUser", authorities = { "DASHBOARD_ADMIN", "DASHBOARD_USER" })
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
	
	@Nested
	@WithMockUser(username = "DashboardUser", authorities = { "DASHBOARD_ADMIN", "DASHBOARD_USER" })
	class TestDelete {
		@Test
		void testDelete() throws Exception {
			Mockito.when(userService.deleteAppClientUser(userDto.getId())).thenReturn(userDto);
			
			mockMvc.perform(delete(ENDPOINT + "{id}", userDto.getId()))
				.andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(userDto)));
		}
		
		@Test
		void testDeleteBadPathVariable() throws Exception {
			mockMvc.perform(delete(ENDPOINT + "{id}", "asdf1234"))
				.andExpect(status().isBadRequest())
				.andExpect(result -> assertTrue(result.getResolvedException() instanceof MethodArgumentTypeMismatchException));
		}
	}
}
