package mil.tron.commonapi.service;

import mil.tron.commonapi.dto.PrivilegeDto;
import mil.tron.commonapi.entity.Privilege;

public interface PrivilegeService {
	Iterable<PrivilegeDto> getPrivileges();
	void deletePrivilege(Privilege privilege);
}
