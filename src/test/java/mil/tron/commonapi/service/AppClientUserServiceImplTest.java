package mil.tron.commonapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.repository.AppClientUserRespository;

@ExtendWith(MockitoExtension.class)
class AppClientUserServiceImplTest {
	@Mock
	private AppClientUserRespository repository;
	
	@InjectMocks
	private AppClientUserServiceImpl userService;
	
	private List<AppClientUser> users;
	
	@BeforeEach
	void setup() {
		users = new ArrayList<>();
		
		AppClientUser a = new AppClientUser();
		a.setId(UUID.randomUUID());
		a.setName("User A");
		a.setPrivileges(new HashSet<Privilege>());
		
		users.add(a);
	}
	
	@Test
    void getAppClientUsersTest() {
    	Mockito.when(repository.findAll()).thenReturn(users);
    	Iterable<AppClientUser> appUsers = userService.getAppClientUsers();
    	List<AppClientUser> result = StreamSupport.stream(appUsers.spliterator(), false).collect(Collectors.toList());
    	assertThat(result).hasSize(1);
    	assertThat(result.get(0)).isEqualTo(users.get(0));
    }
}
