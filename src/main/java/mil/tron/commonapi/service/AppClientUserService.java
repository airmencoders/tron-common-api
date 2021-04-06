package mil.tron.commonapi.service;

import java.util.UUID;

import mil.tron.commonapi.dto.AppClientSummaryDto;
import mil.tron.commonapi.dto.AppClientUserDto;

public interface AppClientUserService {
	Iterable<AppClientSummaryDto> getAppClientUserSummaries();
	Iterable<AppClientUserDto> getAppClientUsers();
	AppClientUserDto createAppClientUser(AppClientUserDto appClient);
	AppClientUserDto updateAppClientUser(UUID id, AppClientUserDto appClient);
	AppClientUserDto deleteAppClientUser(UUID id);
}
