package mil.tron.commonapi.service;

import mil.tron.commonapi.dto.appsource.AppSourceDetailsDto;
import mil.tron.commonapi.dto.appsource.AppSourceDto;

import java.util.List;
import java.util.UUID;

public interface AppSourceService {
    List<AppSourceDto> getAppSources();
    AppSourceDetailsDto createAppSource(AppSourceDetailsDto appSource);
    AppSourceDetailsDto getAppSource(UUID id);
    AppSourceDetailsDto updateAppSource(UUID id, AppSourceDetailsDto appSourceDetailsDto);
    AppSourceDetailsDto deleteAppSource(UUID id);
}
