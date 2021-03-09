package mil.tron.commonapi.service;

import mil.tron.commonapi.dto.appsource.AppSourceDetailsDto;
import mil.tron.commonapi.dto.appsource.AppSourceDto;
import mil.tron.commonapi.dto.appsource.AppSourcePrivDto;

import java.util.List;

public interface AppSourceService {
    List<AppSourceDto> getAppSources();
    AppSourceDetailsDto createAppSource(AppSourceDetailsDto appSource);
}
