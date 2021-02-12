package mil.tron.commonapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import mil.tron.commonapi.dto.AppClientUserDto;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.service.AppClientUserPreAuthenticatedService;
import mil.tron.commonapi.service.AppClientUserService;

@WebMvcTest(AppClientController.class)
@WithMockUser(username = "DASHBOARD_ADMIN", authorities = { "DASHBOARD" })
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
	
	@BeforeEach
	void setup() {
		users = new ArrayList<>();
		
		AppClientUserDto a = new AppClientUserDto();
		a.setId(UUID.randomUUID());
		a.setName("User A");
		a.setPrivileges(new ArrayList<Privilege>());
		
		users.add(a);
	}
	
	@Test
	void testGetAll() throws Exception {
		Mockito.when(userService.getAppClientUsers()).thenReturn(users);
		
		mockMvc.perform(get(ENDPOINT))
			.andExpect(status().isOk())
			.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(users)));
	}
}
