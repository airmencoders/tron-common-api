package mil.tron.commonapi.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import mil.tron.commonapi.dto.UserInfoDto;
import mil.tron.commonapi.exception.BadRequestException;
import org.springframework.stereotype.Service;

@Service
public class UserInfoServiceImpl implements UserInfoService {

	@Override
	public UserInfoDto extractUserInfoFromHeader(String authHeader) {
		if (authHeader == null || authHeader.isBlank()) {
			throw new BadRequestException("Authorization header in request missing.");
		}
		
		String[] splitToken = authHeader.split("Bearer ");
		
		if (splitToken.length != 2) {
			throw new BadRequestException("Authorization header in request is malformed.");
		}
		
		String token = splitToken[1];
		DecodedJWT jwt = JWT.decode(token);
		
		UserInfoDto userInfo = new UserInfoDto();
		userInfo.setGivenName(jwt.getClaim("given_name").asString());
		userInfo.setFamilyName(jwt.getClaim("family_name").asString());
		userInfo.setName(jwt.getClaim("name").asString());
		userInfo.setPreferredUsername(jwt.getClaim("preferred_username").asString());
		userInfo.setEmail(jwt.getClaim("email").asString());
		userInfo.setOrganization(jwt.getClaim("organization").asString());
		userInfo.setRank(jwt.getClaim("rank").asString());
		userInfo.setAffiliation(jwt.getClaim("affiliation").asString());
		
		// usercertificate may not always exist
		String userCert = jwt.getClaim("usercertificate").asString();
		if (userCert != null && !userCert.isBlank()) {
			String[] userCertSplit = userCert.split("\\.");
			
			// usercertificate comes in either the form "last.first.mi.dodid" or "last.first.dodid"
			if (userCertSplit.length < 3) {
				throw new BadRequestException("usercertificate claim is malformed.");
			}
			
			// dodid is the last element
			userInfo.setDodId(userCertSplit[userCertSplit.length - 1]);
		}
		
		return userInfo;
	}

}
