package mil.tron.commonapi.service;

import com.google.common.collect.Sets;
import mil.tron.commonapi.dto.PrivilegeDto;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;

@ExtendWith(MockitoExtension.class)
class PrivilegeServiceImplTest {
	private static final DtoMapper MODEL_MAPPER = new DtoMapper();
	
	@Mock
	private PrivilegeRepository repository;

	@Mock
	private DashboardUserRepository dashboardUserRepository;

	@Mock
	private AppClientUserRespository appClientUserRespository;
	
	@InjectMocks
	private PrivilegeServiceImpl service;
	
	private List<Privilege> privileges;
	private Privilege privilege;
	private PrivilegeDto privilegeDto;
	
	@BeforeEach
	void setup() {
		privileges = new ArrayList<>();
		
		privilege = new Privilege();
		privilege.setId(1L);
		privilege.setName("Privilege A");
		
		privilegeDto = MODEL_MAPPER.map(privilege, PrivilegeDto.class);
		
		privileges.add(privilege);
	}
	
	@Test
    void getPrivileges() {
    	Mockito.when(repository.findAll()).thenReturn(privileges);
    	Iterable<PrivilegeDto> getPrivilegesResult = service.getPrivileges();
    	List<PrivilegeDto> resultAsList = StreamSupport.stream(getPrivilegesResult.spliterator(), false).collect(Collectors.toList());
    	assertThat(resultAsList).hasSize(1);
    	assertThat(resultAsList.get(0)).isEqualTo(privilegeDto);
    }

    @Test
	void deletePrivilege() {
		Mockito.when(dashboardUserRepository.save(Mockito.any())).then(returnsFirstArg());
		Mockito.when(appClientUserRespository.save(Mockito.any())).then(returnsFirstArg());

		Privilege privilege = Privilege
				.builder()
				.id(1L)
				.name("Person-test")
				.build();

		Privilege privilege2 = Privilege
				.builder()
				.id(2L)
				.name("Organization-test")
				.build();

		DashboardUser user = DashboardUser.builder()
				.privileges(Sets.newHashSet(privilege, privilege2))
				.build();

		AppClientUser appClientUser = AppClientUser.builder()
				.privileges(Sets.newHashSet(privilege, privilege2))
				.build();

		Mockito.when(dashboardUserRepository.findAll()).thenReturn(Lists.newArrayList(user));
		Mockito.when(appClientUserRespository.findAll()).thenReturn(Lists.newArrayList(appClientUser));

		assertTrue(user.getPrivileges().contains(privilege));
		assertTrue(appClientUser.getPrivileges().contains(privilege));
		service.deletePrivilege(privilege);
		assertFalse(user.getPrivileges().contains(privilege));
		assertFalse(appClientUser.getPrivileges().contains(privilege));
	}
	
}
