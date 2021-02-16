package mil.tron.commonapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import mil.tron.commonapi.dto.PrivilegeDto;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.repository.PrivilegeRepository;

@ExtendWith(MockitoExtension.class)
class PrivilegeServiceImplTest {
	private static final DtoMapper MODEL_MAPPER = new DtoMapper();
	
	@Mock
	private PrivilegeRepository repository;
	
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
	
}
