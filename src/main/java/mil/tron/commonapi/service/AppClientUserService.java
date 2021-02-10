package mil.tron.commonapi.service;

import java.util.UUID;

import mil.tron.commonapi.dto.AppClientUserDto;

public interface AppClientUserService {
	Iterable<AppClientUserDto> getAppClientUsers();
	AppClientUserDto updateAppClientUser(UUID id, AppClientUserDto appClient);
}
