package mil.tron.commonapi.service;

import mil.tron.commonapi.dto.appsource.AppEndPointPrivDto;
import mil.tron.commonapi.dto.appsource.AppSourceDetailsDto;
import mil.tron.commonapi.dto.appsource.AppSourceDto;
import mil.tron.commonapi.entity.DashboardUser;
import org.springframework.core.io.Resource;

import java.util.List;
import java.util.UUID;

public interface AppSourceService {
    List<AppSourceDto> getAppSources();
    AppSourceDetailsDto createAppSource(AppSourceDetailsDto appSource);
    AppSourceDetailsDto getAppSource(UUID id);
    AppSourceDetailsDto updateAppSource(UUID id, AppSourceDetailsDto appSourceDetailsDto);
    AppSourceDetailsDto deleteAppSource(UUID id);
    Resource getApiSpecForAppSource(UUID id);
    Resource getApiSpecForAppSourceByEndpointPriv(UUID id);

    // app source and app client endpoint management
    AppSourceDetailsDto deleteAllAppClientPrivs(UUID appSourceId);
    AppSourceDetailsDto addEndPointPrivilege(AppEndPointPrivDto dto);
    AppSourceDetailsDto removeEndPointPrivilege(UUID appSourceId, UUID appSourceEndPointPrivId);

    // app source admin user management
    AppSourceDetailsDto addAppSourceAdmin(UUID appSourceId, String email);
    AppSourceDetailsDto removeAdminFromAppSource(UUID appSourceId, String email);
    void deleteAdminFromAllAppSources(DashboardUser user);

    boolean userIsAdminForAppSource(UUID appId, String email);
    boolean userIsAdminForAppSourceByEndpoint(UUID endpointId, String email);
}
