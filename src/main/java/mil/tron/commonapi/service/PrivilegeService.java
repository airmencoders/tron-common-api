package mil.tron.commonapi.service;

import mil.tron.commonapi.dto.PrivilegeDto;

public interface PrivilegeService {
	Iterable<PrivilegeDto> getPrivileges();
}
