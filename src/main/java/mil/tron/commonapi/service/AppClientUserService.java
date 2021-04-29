package mil.tron.commonapi.service;

import mil.tron.commonapi.dto.AppClientSummaryDto;
import mil.tron.commonapi.dto.AppClientUserDto;
import mil.tron.commonapi.entity.DashboardUser;

import java.util.UUID;

public interface AppClientUserService {
	Iterable<AppClientSummaryDto> getAppClientUserSummaries();
	Iterable<AppClientUserDto> getAppClientUsers();
	AppClientUserDto getAppClient(UUID id);
	AppClientUserDto createAppClientUser(AppClientUserDto appClient);
	AppClientUserDto updateAppClientUser(UUID id, AppClientUserDto appClient);
	AppClientUserDto deleteAppClientUser(UUID id);
	AppClientUserDto updateAppClientDeveloperItems(UUID id, AppClientUserDto appClient);
	boolean userIsAppClientDeveloperForApp(UUID id, String email);
	void deleteDeveloperFromAllAppClient(DashboardUser user);
}
