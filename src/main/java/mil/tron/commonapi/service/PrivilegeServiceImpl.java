package mil.tron.commonapi.service;

import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.stereotype.Service;

import mil.tron.commonapi.dto.PrivilegeDto;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.repository.PrivilegeRepository;

@Service
public class PrivilegeServiceImpl implements PrivilegeService {
	private static final DtoMapper MODEL_MAPPER = new DtoMapper();
	
	private PrivilegeRepository privilegeRepo;
	
	public PrivilegeServiceImpl(PrivilegeRepository privilegeRepo) {
		this.privilegeRepo = privilegeRepo;
	}
	
	@Override
	public Iterable<PrivilegeDto> getPrivileges() {
		return StreamSupport.stream(privilegeRepo.findAll().spliterator(), false).map(this::convertToDto).collect(Collectors.toList());
	}
	
	private PrivilegeDto convertToDto(Privilege privilege) {
		return MODEL_MAPPER.map(privilege, PrivilegeDto.class);
	}
}
