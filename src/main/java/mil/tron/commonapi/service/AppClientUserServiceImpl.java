package mil.tron.commonapi.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.modelmapper.Converter;
import org.modelmapper.PropertyMap;
import org.modelmapper.spi.MappingContext;
import org.springframework.stereotype.Service;

import mil.tron.commonapi.dto.AppClientUserDto;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.AppClientUserRespository;

@Service
public class AppClientUserServiceImpl implements AppClientUserService {
	private static final DtoMapper MODEL_MAPPER = new DtoMapper();
	
	private AppClientUserRespository userRepository;
	
	public AppClientUserServiceImpl(AppClientUserRespository userRepository) {
		this.userRepository = userRepository;
		
		Converter<List<Privilege>, Set<Privilege>> convertPrivilegesToSet = 
				((MappingContext<List<Privilege>, Set<Privilege>> context) -> new HashSet<>(context.getSource()));
		
		Converter<Set<Privilege>, List<Privilege>> convertPrivilegesToArr = 
				((MappingContext<Set<Privilege>, List<Privilege>> context) -> new ArrayList<>(context.getSource()));
		
		MODEL_MAPPER.addConverter(convertPrivilegesToSet);
		MODEL_MAPPER.addConverter(convertPrivilegesToArr);
	}
	
	@Override
	public Iterable<AppClientUserDto> getAppClientUsers() {
		return StreamSupport.stream(userRepository.findAll().spliterator(), false).map(this::convertToDto).collect(Collectors.toList());
	}
	
	@Override
	public AppClientUserDto updateAppClientUser(UUID id, AppClientUserDto appClient) {
		if (!id.equals(appClient.getId()))
			throw new InvalidRecordUpdateRequest(String.format("ID: %s does not match the resource ID: %s", id, appClient.getId()));
		
		Optional<AppClientUser> dbUser = userRepository.findById(id);
		
		if (dbUser.isEmpty())
			throw new RecordNotFoundException("Resource with the ID: " + id + " does not exist.");
		
		AppClientUser entity = convertToEntity(appClient);
		
		return convertToDto(userRepository.save(entity));
			
	}
	
	private AppClientUserDto convertToDto(AppClientUser user) {
		return MODEL_MAPPER.map(user, AppClientUserDto.class);
	}
	
	private AppClientUser convertToEntity(AppClientUserDto user) {
		return MODEL_MAPPER.map(user, AppClientUser.class);
	}
}
