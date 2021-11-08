package mil.tron.commonapi.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.dto.UserInfoDto;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.PersonRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserInfoServiceImpl implements UserInfoService {

	private final PersonRepository personRepository;
	private final PersonService personService;

	public UserInfoServiceImpl(PersonRepository personRepository, PersonService personService) {
		this.personRepository = personRepository;
		this.personService = personService;
	}

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
		
		// include the expire time of the token
		Claim expireTimeClaim = jwt.getClaim("exp");
		if (!expireTimeClaim.isNull()) {
			userInfo.setExpireTime(expireTimeClaim.asLong());
		}
		
		return userInfo;
	}

	@Override
	public PersonDto getExistingPersonFromUser(String authHeader) {
		final UserInfoDto userInfo = this.extractUserInfoFromHeader(authHeader);
		final String userEmail = userInfo.getEmail();
		Optional<Person> person = this.personRepository.findByEmailIgnoreCase(userEmail);
		if (person.isEmpty()) {
			throw new RecordNotFoundException("This user does not have an existing person record.");
		}
		final PersonDto personDto = this.personService.convertToDto(person.get(), new PersonConversionOptions());
		return personDto;
	}

}
