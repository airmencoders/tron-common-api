package mil.tron.commonapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.dto.PrivilegeDto;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.service.AppClientUserPreAuthenticatedService;
import mil.tron.commonapi.service.PrivilegeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import mil.tron.commonapi.dto.response.WrappedResponse;


@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
@AutoConfigureMockMvc
class PrivilegeControllerTest {
	private static final String ENDPOINT = "/v1/privilege/";
	private static final String ENDPOINT_V2 = "/v2/privilege/";
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final DtoMapper MODEL_MAPPER = new DtoMapper();
	
	@Autowired
	private MockMvc mockMvc;
	
	@MockBean
	private AppClientUserPreAuthenticatedService appClientUserPreAuthenticatedService;
	
	@MockBean
	private PrivilegeService service;
	
	private List<PrivilegeDto> privileges;
	private Privilege privilege;
	private PrivilegeDto privilegeDto;
	
	@BeforeEach
	void setup() {
		privilege = new Privilege();
		privilege.setId(1L);
		privilege.setName("A Privilege");
		
		privilegeDto = MODEL_MAPPER.map(privilege, PrivilegeDto.class);
		
		privileges = new ArrayList<>();
		privileges.add(privilegeDto);
	}
	
	@Nested
	class TestGet {
		@Test
		void getAll() throws Exception {
			Mockito.when(service.getPrivileges()).thenReturn(privileges);
			
			mockMvc.perform(get(ENDPOINT))
				.andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(privileges)));
		}	
		
		@Test
		void getAllWrapped() throws Exception {
			Mockito.when(service.getPrivileges()).thenReturn(privileges);
			
			WrappedResponse<List<PrivilegeDto>> personWrappedResponse = WrappedResponse.<List<PrivilegeDto>>builder().data(privileges).build();
			
			mockMvc.perform(get(ENDPOINT_V2))
				.andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(personWrappedResponse)));
		}	
	}
}
