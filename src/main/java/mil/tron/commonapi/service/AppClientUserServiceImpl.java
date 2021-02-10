package mil.tron.commonapi.service;

import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.stereotype.Service;

import mil.tron.commonapi.dto.AppClientUserDto;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.repository.AppClientUserRespository;

@Service
public class AppClientUserServiceImpl implements AppClientUserService {
	private static final DtoMapper MODEL_MAPPER = new DtoMapper();
	
	private AppClientUserRespository userRepository;
	
	public AppClientUserServiceImpl(AppClientUserRespository userRepository) {
		this.userRepository = userRepository;
	}
	
	@Override
	public Iterable<AppClientUserDto> getAppClientUsers() {
		return StreamSupport.stream(userRepository.findAll().spliterator(), false).map(this::convertToDto).collect(Collectors.toList());
	}
	
	private AppClientUserDto convertToDto(AppClientUser user) {
		return MODEL_MAPPER.map(user, AppClientUserDto.class);
	}
}
