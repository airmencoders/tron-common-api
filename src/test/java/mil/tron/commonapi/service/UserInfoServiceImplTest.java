package mil.tron.commonapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import mil.tron.commonapi.dto.UserInfoDto;
import mil.tron.commonapi.exception.BadRequestException;

@ExtendWith(MockitoExtension.class)
class UserInfoServiceImplTest {
	private static final String ORGANIZATION = "Test Org";
	private static final String NAME = "Test JWT";
	private static final String PREFERRED_USERNAME = "testjwt";
	private static final String GIVEN_NAME = "Test";
	private static final String FAMILY_NAME = "JWT";
	private static final String EMAIL = "test.jwt@email.com";
	private static final String DOD_ID = "1234567890";
	private static final String USER_CERTIFICATE = String.format("%s.%s.%s", FAMILY_NAME, GIVEN_NAME, DOD_ID);
	private static final String USER_CERTIFICATE_MALFORMED = String.format("%s.%s", GIVEN_NAME, DOD_ID);
	
	private static final Algorithm ALGORITHM = Algorithm.HMAC256("secret");
	
	private static final String TOKEN = JWT.create()
	    	.withClaim("organization", ORGANIZATION)
	    	.withClaim("name", NAME)
	    	.withClaim("preferred_username", PREFERRED_USERNAME)
	    	.withClaim("given_name", GIVEN_NAME)
	    	.withClaim("family_name", FAMILY_NAME)
	    	.withClaim("email", EMAIL)
	        .withIssuer("tests")
	        .sign(ALGORITHM);
	
	private static final String TOKEN_WITH_DOD_ID = JWT.create()
	    	.withClaim("organization", ORGANIZATION)
	    	.withClaim("name", NAME)
	    	.withClaim("preferred_username", PREFERRED_USERNAME)
	    	.withClaim("given_name", GIVEN_NAME)
	    	.withClaim("family_name", FAMILY_NAME)
	    	.withClaim("email", EMAIL)
	    	.withClaim("usercertificate", USER_CERTIFICATE)
	        .withIssuer("tests")
	        .sign(ALGORITHM);
	
	private static final String TOKEN_WITH_MALFORMED_DOD_ID = JWT.create()
	    	.withClaim("organization", ORGANIZATION)
	    	.withClaim("name", NAME)
	    	.withClaim("preferred_username", PREFERRED_USERNAME)
	    	.withClaim("given_name", GIVEN_NAME)
	    	.withClaim("family_name", FAMILY_NAME)
	    	.withClaim("email", EMAIL)
	    	.withClaim("usercertificate", USER_CERTIFICATE_MALFORMED)
	        .withIssuer("tests")
	        .sign(ALGORITHM);
	
	private static final String TOKEN_WITH_BLANK_DOD_ID = JWT.create()
	    	.withClaim("organization", ORGANIZATION)
	    	.withClaim("name", NAME)
	    	.withClaim("preferred_username", PREFERRED_USERNAME)
	    	.withClaim("given_name", GIVEN_NAME)
	    	.withClaim("family_name", FAMILY_NAME)
	    	.withClaim("email", EMAIL)
	    	.withClaim("usercertificate", "")
	        .withIssuer("tests")
	        .sign(ALGORITHM);
	
	@InjectMocks
	UserInfoServiceImpl service;
	
	@Test
	void testNullAndBlankAuthHeader() {
		assertThatExceptionOfType(BadRequestException.class).isThrownBy(() -> service.extractUserInfoFromHeader(null));
		assertThatExceptionOfType(BadRequestException.class).isThrownBy(() -> service.extractUserInfoFromHeader(""));
	}
	
	@Test
	void testMalformedAuthHeader() {
		assertThatExceptionOfType(BadRequestException.class).isThrownBy(() -> service.extractUserInfoFromHeader("Bearer "));
		assertThatExceptionOfType(BadRequestException.class).isThrownBy(() -> service.extractUserInfoFromHeader("Test"));
	}
	
	@Test
	void testUserCertMalformed() {
		String header = "Bearer " + TOKEN_WITH_MALFORMED_DOD_ID;
		assertThatExceptionOfType(BadRequestException.class).isThrownBy(() -> service.extractUserInfoFromHeader(header));
	}
	
	@Test
	void testSuccess_dodId_exists() {
		String header = "Bearer " + TOKEN_WITH_DOD_ID;
		
		UserInfoDto userInfo = new UserInfoDto();
		userInfo.setDodId(DOD_ID);
		userInfo.setEmail(EMAIL);
		userInfo.setFamilyName(FAMILY_NAME);
		userInfo.setGivenName(GIVEN_NAME);
		userInfo.setName(NAME);
		userInfo.setOrganization(ORGANIZATION);
		userInfo.setPreferredUsername(PREFERRED_USERNAME);
		
		assertThat(service.extractUserInfoFromHeader(header)).isEqualTo(userInfo);
		
		userInfo.setDodId(null);
		String blankCertHeader = "Bearer " + TOKEN_WITH_BLANK_DOD_ID;
		assertThat(service.extractUserInfoFromHeader(blankCertHeader)).isEqualTo(userInfo);
	}
	
	@Test
	void testSuccess_dodId_notExists() {
		String header = "Bearer " + TOKEN;
		
		UserInfoDto userInfo = new UserInfoDto();
		userInfo.setEmail(EMAIL);
		userInfo.setFamilyName(FAMILY_NAME);
		userInfo.setGivenName(GIVEN_NAME);
		userInfo.setName(NAME);
		userInfo.setOrganization(ORGANIZATION);
		userInfo.setPreferredUsername(PREFERRED_USERNAME);
		
		assertThat(service.extractUserInfoFromHeader(header)).isEqualTo(userInfo);
	}
	
}
