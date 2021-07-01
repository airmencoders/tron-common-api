package mil.tron.commonapi.service;

import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.dto.UserInfoDto;

public interface UserInfoService {
	UserInfoDto extractUserInfoFromHeader(String authHeader);

	PersonDto getExistingPersonFromUser(String authHeader);
}
