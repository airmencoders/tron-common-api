package mil.tron.commonapi.service;


import org.springframework.core.io.Resource;

import mil.tron.commonapi.dto.LogfileDto;

public interface LogfileService {
	Iterable<LogfileDto> getAllLogfileInfo();
	Resource getLogfileResource(String fileName);
	String getLogfileResourceName(Resource resource);
}
