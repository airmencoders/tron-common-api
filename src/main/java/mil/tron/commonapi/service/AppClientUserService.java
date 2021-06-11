package mil.tron.commonapi.service;

import mil.tron.commonapi.dto.appclient.AppClientSummaryDto;
import mil.tron.commonapi.dto.appclient.AppClientUserDetailsDto;
import mil.tron.commonapi.dto.appclient.AppClientUserDto;
import mil.tron.commonapi.entity.DashboardUser;

import java.util.UUID;

public interface AppClientUserService {
	Iterable<AppClientSummaryDto> getAppClientUserSummaries();
	Iterable<AppClientUserDto> getAppClientUsers();
	AppClientUserDetailsDto getAppClient(UUID id);
	AppClientUserDto createAppClientUser(AppClientUserDto appClient);
	AppClientUserDto updateAppClientUser(UUID id, AppClientUserDto appClient);
	AppClientUserDto deleteAppClientUser(UUID id);
	AppClientUserDto updateAppClientDeveloperItems(UUID id, AppClientUserDto appClient);
	boolean userIsAppClientDeveloperForApp(UUID id, String email);
	boolean userIsAppClientDeveloperForAppSubscription(UUID subscriptionId, String user);
	void deleteDeveloperFromAllAppClient(DashboardUser user);
}
