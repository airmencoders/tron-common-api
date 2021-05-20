package mil.tron.commonapi.controller;

import mil.tron.commonapi.MockToken;
import mil.tron.commonapi.dto.UserInfoDto;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.service.UserInfoService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserInfoControllerTest {
	private static final String ENDPOINT = "/v1/userinfo/";
	
	@Autowired
	private MockMvc mockMvc;
	
	@MockBean
	private UserInfoService userInfoService;
	
	@Test
	void testGetSuccess() throws Exception {
		Mockito.when(userInfoService.extractUserInfoFromHeader(Mockito.anyString())).thenReturn(new UserInfoDto());
		
		mockMvc.perform(get(ENDPOINT).header("authorization", MockToken.token))
			.andExpect(status().isOk());
	}
	
	@Test
	void testGetBadRequest() throws Exception {
		Mockito.when(userInfoService.extractUserInfoFromHeader(Mockito.any())).thenThrow(new BadRequestException());
		
		mockMvc.perform(get(ENDPOINT))
			.andExpect(status().isBadRequest());
	}
}
