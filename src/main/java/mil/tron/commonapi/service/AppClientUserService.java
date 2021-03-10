package mil.tron.commonapi.service;

import java.util.UUID;
import mil.tron.commonapi.dto.AppClientUserDto;

public interface AppClientUserService {
	Iterable<AppClientUserDto> getAppClientUsers();
	AppClientUserDto createAppClientUser(AppClientUserDto appClient);
	AppClientUserDto updateAppClientUser(UUID id, AppClientUserDto appClient);
	void deleteAppClientUser(UUID id);
}
