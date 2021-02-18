package mil.tron.commonapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import mil.tron.commonapi.dto.AppClientUserDto;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.AppClientUserRespository;

@ExtendWith(MockitoExtension.class)
class AppClientUserServiceImplTest {
	private static final DtoMapper MODEL_MAPPER = new DtoMapper();
	
	@Mock
	private AppClientUserRespository repository;
	
	@InjectMocks
	private AppClientUserServiceImpl userService;
	
	private List<AppClientUser> users;
	private AppClientUser user;
	private AppClientUserDto userDto;
	
	@BeforeEach
	void setup() {
		users = new ArrayList<>();
		
		user = new AppClientUser();
		user.setId(UUID.randomUUID());
		user.setName("User A");
		user.setPrivileges(new HashSet<Privilege>());
		
		userDto = MODEL_MAPPER.map(user, AppClientUserDto.class);
		
		users.add(user);
	}
	
	@Test
    void getAppClientUsersTest() {
    	Mockito.when(repository.findAll()).thenReturn(users);
    	Iterable<AppClientUserDto> appUsers = userService.getAppClientUsers();
    	List<AppClientUserDto> result = StreamSupport.stream(appUsers.spliterator(), false).collect(Collectors.toList());
    	assertThat(result).hasSize(1);
    	assertThat(result.get(0)).isEqualTo(MODEL_MAPPER.map(users.get(0), AppClientUserDto.class));
    }
	
	@Nested 
	class CreateAppClientUserTest {
		@Test
		void success() {
			Mockito.when(repository.findByNameIgnoreCase(user.getName())).thenReturn(Optional.ofNullable(null));
			Mockito.when(repository.save(Mockito.any(AppClientUser.class))).thenReturn(user);
			
			AppClientUserDto result = userService.createAppClientUser(userDto);
			assertThat(result).isEqualTo(userDto);
		}
		
		@Test
		void resourceAlreadyExists() {
			Mockito.when(repository.findByNameIgnoreCase(user.getName())).thenReturn(Optional.of(user));
			
			assertThrows(ResourceAlreadyExistsException.class, () -> userService.createAppClientUser(userDto));
		}
	}
	
	@Nested
	class UpdateAppClientUser {
		@Test
		void idNotMatching() {
	    	assertThrows(InvalidRecordUpdateRequest.class, () -> userService.updateAppClientUser(UUID.randomUUID(), userDto));
		}
		
		@Test
		void idNotExist() {
			// Test id not exist
	    	Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.ofNullable(null));
	    	assertThrows(RecordNotFoundException.class, () -> userService.updateAppClientUser(userDto.getId(), userDto));
		}
		
		@Test
		void nameAlreadyExists() {
			String changedName = "Some different name";
			userDto.setName(changedName);
			
			AppClientUser existingUser = new AppClientUser();
			existingUser.setId(UUID.randomUUID());
			existingUser.setName(changedName);
			existingUser.setPrivileges(new HashSet<Privilege>());
			
			Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(user));
			Mockito.when(repository.findByNameIgnoreCase(Mockito.anyString())).thenReturn(Optional.of(existingUser));
			
			assertThrows(InvalidRecordUpdateRequest.class, () -> userService.updateAppClientUser(userDto.getId(), userDto));
		}
		
		@Test
		void successfulUpdate_NameChange() {
			userDto.setName("Some Different Name");
			
	    	Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(user));
	    	Mockito.when(repository.findByNameIgnoreCase(Mockito.anyString())).thenReturn(Optional.ofNullable(null));
	    	Mockito.when(repository.save(Mockito.any(AppClientUser.class))).thenReturn(MODEL_MAPPER.map(userDto, AppClientUser.class));
	    	
	    	AppClientUserDto updatedUser = userService.updateAppClientUser(userDto.getId(), userDto);
	    	assertThat(updatedUser).isEqualTo(userDto);
		}
		
		@Test
		void successfulUpdate_NoNameChange() {
	    	Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(user));
	    	Mockito.when(repository.save(Mockito.any(AppClientUser.class))).thenReturn(user);
	    	
	    	
	    	userDto.setName(null);
	    	AppClientUserDto updatedUser = userService.updateAppClientUser(userDto.getId(), userDto);
	    	assertThat(updatedUser).isEqualTo(MODEL_MAPPER.map(user, AppClientUserDto.class));
		}
	}
}
