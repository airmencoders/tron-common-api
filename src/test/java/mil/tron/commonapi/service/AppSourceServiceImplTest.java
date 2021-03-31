package mil.tron.commonapi.service;

import lombok.val;
import mil.tron.commonapi.dto.AppClientUserPrivDto;
import mil.tron.commonapi.dto.appsource.AppSourceDetailsDto;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.entity.appsource.AppEndpointPriv;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import mil.tron.commonapi.repository.appsource.AppEndpointPrivRepository;
import mil.tron.commonapi.repository.appsource.AppSourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
public class AppSourceServiceImplTest {

    @Mock
    private AppSourceRepository appSourceRepository;
    
    @Mock
    private AppEndpointPrivRepository appSourcePrivRepo;
    
    @Mock
    private AppClientUserRespository appClientUserRepo;
    
    @Mock
    private PrivilegeRepository privilegeRepo;

    @InjectMocks
    private AppSourceServiceImpl service;

    private static UUID APP_SOURCE_UUID = UUID.randomUUID();
    private static String APP_SOURCE_NAME = "Test App Source";
    private List<AppSource> entries = new ArrayList<>();
    private Set<AppEndpointPriv> appSourcePrivs = new HashSet<>();
    private AppSource appSource;
    private AppSourceDetailsDto appSourceDetailsDto;
    private List<AppClientUserPrivDto> appClientUserPrivDtos;
    private Set<Privilege> privileges;
    private AppClientUser appClientUser;

    @BeforeEach
    void setup() {
    	privileges = new HashSet<>();
    	privileges.add(
			Privilege
    			.builder()
    			.id(1L)
    			.name("Read")
    			.build()
		);
    	privileges.add(
			Privilege
    			.builder()
    			.id(2L)
    			.name("Write")
    			.build()
		);
    	
        this.appSource = AppSource
                .builder()
                .id(APP_SOURCE_UUID)
                .name(APP_SOURCE_NAME)
                .build();
        appClientUser = AppClientUser
                .builder()
                .id(UUID.randomUUID())
                .name("Test App Client")
                .build();
        val appSourcePriv = AppEndpointPriv
                .builder()
                .id(UUID.randomUUID())
                .appSource(appSource)
                .appClientUser(appClientUser)
                .privileges(privileges)
                .build();
        appSourcePrivs.add(
            appSourcePriv
        );
        appSource.setAppSourcePrivs(appSourcePrivs);
        appClientUser.setAppSourcePrivs(appSourcePrivs);
        entries.add(appSource);

        List<Privilege> privilegesList = new ArrayList<>(privileges);
        
        appClientUserPrivDtos = new ArrayList<>();
        appClientUserPrivDtos.add(
    		AppClientUserPrivDto
        		.builder()
        		.appClientUser(appClientUser.getId())
        		.privilegeIds(Arrays.asList(privilegesList.get(0).getId(), privilegesList.get(1).getId()))
        		.build()
		);
        
        appSourceDetailsDto = AppSourceDetailsDto
        		.builder()
        		.id(appSource.getId())
        		.name(appSource.getName())
        		.appClients(appClientUserPrivDtos)
        		.build();
    }

    @Nested
    class Get {
    	@Test
        void getAppSources() {
            Mockito.when(appSourceRepository.findAll()).thenReturn(entries);
            assertEquals(1, service.getAppSources().size());
        }

        @Test
        void getAppSourceDetails() {
            Mockito.when(appSourceRepository.findById(APP_SOURCE_UUID)).thenReturn(
                    Optional.of(appSource));
            assertEquals(APP_SOURCE_NAME, service.getAppSource(APP_SOURCE_UUID).getName());
        }
        
        @Test
        void getAppSourceDetails_notFound() {
        	Mockito.when(appSourceRepository.findById(APP_SOURCE_UUID)).thenReturn(Optional.ofNullable(null));
        	assertThrows(RecordNotFoundException.class, () -> service.getAppSource(APP_SOURCE_UUID));
        }
    }
    
    
    @Nested
    class Update {
    	@Test
        void idNotMatching() {
        	assertThrows(InvalidRecordUpdateRequest.class, () -> service.updateAppSource(UUID.randomUUID(), appSourceDetailsDto));
        }
        
        @Test
        void idNotExists() {
        	Mockito.when(appSourceRepository.findById(Mockito.any(UUID.class))).thenReturn(Optional.ofNullable(null));
        	
        	assertThrows(RecordNotFoundException.class, () -> service.updateAppSource(appSourceDetailsDto.getId(), appSourceDetailsDto));
        }
        
        @Test
        void nameAlreadyExists() {
        	Mockito.when(appSourceRepository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(appSource));
        	Mockito.when(appSourceRepository.existsByNameIgnoreCase(Mockito.anyString())).thenReturn(true);
        	
        	AppSourceDetailsDto toUpdate = AppSourceDetailsDto
            		.builder()
            		.id(appSource.getId())
            		.name("New Name")
            		.appClients(appClientUserPrivDtos)
            		.build();
        	
        	assertThrows(ResourceAlreadyExistsException.class, () -> service.updateAppSource(toUpdate.getId(), toUpdate));
        }
        
        @Test
        void successUpdate() {
        	Mockito.when(appSourceRepository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(appSource));

        	appClientUserPrivDtos.remove(0);
        	AppSourceDetailsDto toUpdate = AppSourceDetailsDto
            		.builder()
            		.id(appSource.getId())
            		.name(appSource.getName())
            		.appClients(appClientUserPrivDtos)
            		.build();
        	
        	Mockito.when(appSourceRepository.saveAndFlush(Mockito.any())).thenReturn(AppSource.builder().id(toUpdate.getId()).name(toUpdate.getName()).build());
        	
        	List<AppEndpointPriv> existingPrivs = new ArrayList<>();
        	Mockito.when(appSourcePrivRepo.findAllByAppSource(Mockito.any(AppSource.class))).thenReturn(existingPrivs);
        	
        	AppSourceDetailsDto updated = service.updateAppSource(toUpdate.getId(), toUpdate);
        	
        	assertThat(updated).isEqualTo(toUpdate);
        }
    }
    
    @Nested
    class Delete {
    	@Test
    	void notExists() {
    		Mockito.when(appSourceRepository.findById(Mockito.any(UUID.class))).thenReturn(Optional.ofNullable(null));
    		
    		assertThrows(RecordNotFoundException.class, () -> service.deleteAppSource(appSource.getId()));
    	}
    	
    	@Test
    	void successDelete() {
    		Mockito.when(appSourceRepository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(appSource));

    		AppSourceDetailsDto removed = service.deleteAppSource(appSource.getId());

    		assertThat(removed).isEqualTo(appSourceDetailsDto);
    	}
    }
    
    @Test
    void testCreateAppSource() {
    	Mockito.when(appSourceRepository.saveAndFlush(Mockito.any())).thenReturn(AppSource.builder().id(appSourceDetailsDto.getId()).name(appSourceDetailsDto.getName()).build());
    	
    	List<AppEndpointPriv> existingPrivs = new ArrayList<>();
    	Mockito.when(appSourcePrivRepo.findAllByAppSource(Mockito.any(AppSource.class))).thenReturn(existingPrivs);
    	
    	Mockito.when(appClientUserRepo.findById(Mockito.any())).thenReturn(Optional.of(appClientUser));
    	Mockito.when(privilegeRepo.existsById(Mockito.anyLong())).thenReturn(true);
    	
    	AppSourceDetailsDto created = service.createAppSource(appSourceDetailsDto);
    	
    	appSourceDetailsDto.setId(created.getId());
    	
    	assertThat(created).isEqualTo(appSourceDetailsDto);
    }

}
