package mil.tron.commonapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;

import mil.tron.commonapi.dto.UserInfoDto;

@WebMvcTest(UserInfoController.class)
class UserInfoControllerTest {
	private static final String ENDPOINT = "/v1/userinfo/";
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	
	private static final String ORGANIZATION = "Test Org";
	private static final String NAME = "Test JWT";
	private static final String PREFERRED_USERNAME = "testjwt";
	private static final String GIVEN_NAME = "Test";
	private static final String FAMILY_NAME = "JWT";
	private static final String EMAIL = "test.jwt@email.com";
	
	private static final Algorithm ALGORITHM = Algorithm.HMAC256("secret");
	private static final String token = JWT.create()
	    	.withClaim("organization", ORGANIZATION)
	    	.withClaim("name", NAME)
	    	.withClaim("preferred_username", PREFERRED_USERNAME)
	    	.withClaim("given_name", GIVEN_NAME)
	    	.withClaim("family_name", FAMILY_NAME)
	    	.withClaim("email", EMAIL)
	        .withIssuer("tests")
	        .sign(ALGORITHM);
	
	@Autowired
	private MockMvc mockMvc;
	
	@Test
	void testGetSuccess() throws Exception {
		UserInfoDto userInfo = new UserInfoDto();
		userInfo.setEmail(EMAIL);
		userInfo.setOrganization(ORGANIZATION);
		userInfo.setName(NAME);
		userInfo.setPreferredUsername(PREFERRED_USERNAME);
		userInfo.setFamilyName(FAMILY_NAME);
		userInfo.setGivenName(GIVEN_NAME);
		
		mockMvc.perform(get(ENDPOINT).header("authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(userInfo)));
	}
	
	@Test
	void testGetFailNoAuthorizationHeader() throws Exception {
		// No auth header
		mockMvc.perform(get(ENDPOINT))
			.andExpect(status().isBadRequest())
			.andExpect(result -> result.getResponse().getContentAsString().contains("missing"));
		
		// Blank auth header
		mockMvc.perform(get(ENDPOINT).header("authorization", " "))
			.andExpect(status().isBadRequest())
			.andExpect(result -> result.getResponse().getContentAsString().contains("blank"));
	}
	
	@Test
	void testGetFailMalformedAuthorizationHeader() throws Exception {
		mockMvc.perform(get(ENDPOINT).header("authorization", token))
			.andExpect(status().isBadRequest());
	}
}
