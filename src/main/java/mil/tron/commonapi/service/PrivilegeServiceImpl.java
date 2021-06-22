package mil.tron.commonapi.service;

import mil.tron.commonapi.dto.PrivilegeDto;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class PrivilegeServiceImpl implements PrivilegeService {
	private static final DtoMapper MODEL_MAPPER = new DtoMapper();
	
	private PrivilegeRepository privilegeRepo;
	private DashboardUserRepository dashboardUserRepository;
	private AppClientUserRespository appClientUserRespository;
	
	public PrivilegeServiceImpl(PrivilegeRepository privilegeRepo,
								DashboardUserRepository dashboardUserRepository,
								AppClientUserRespository appClientUserRespository) {
		this.privilegeRepo = privilegeRepo;
		this.dashboardUserRepository = dashboardUserRepository;
		this.appClientUserRespository = appClientUserRespository;
	}
	
	@Override
	public Iterable<PrivilegeDto> getPrivileges() {
		return StreamSupport.stream(privilegeRepo.findAll().spliterator(), false).map(this::convertToDto).collect(Collectors.toList());
	}

	/**
	 * Purges a privilege from the system, it will reach into the consumers of privileges
	 * like AppClientUsers and DashboardUsers and remove them from there if needed so we dont
	 * hit a Foreign Key violation
	 * @param privilege the privilege object to delete
	 */
	@Override
	public void deletePrivilege(Privilege privilege) {
		for (DashboardUser user : dashboardUserRepository.findAll()) {
			if (user.getPrivileges().contains(privilege)) {
				Set<Privilege> privs = user.getPrivileges();
				privs.remove(privilege);
				dashboardUserRepository.save(user);
			}
		}

		for (AppClientUser user : appClientUserRespository.findAll()) {
			if (user.getPrivileges().contains(privilege)) {
				Set<Privilege> privs = user.getPrivileges();
				privs.remove(privilege);
				appClientUserRespository.save(user);
			}
		}

		privilegeRepo.deleteById(privilege.getId());
	}

	private PrivilegeDto convertToDto(Privilege privilege) {
		return MODEL_MAPPER.map(privilege, PrivilegeDto.class);
	}
}
