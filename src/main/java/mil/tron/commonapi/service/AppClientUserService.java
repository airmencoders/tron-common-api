package mil.tron.commonapi.service;

import mil.tron.commonapi.dto.AppClientUserDto;

public interface AppClientUserService {
	Iterable<AppClientUserDto> getAppClientUsers();
}
