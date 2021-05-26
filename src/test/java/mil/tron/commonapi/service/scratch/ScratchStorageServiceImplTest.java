package mil.tron.commonapi.service.scratch;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.jayway.jsonpath.JsonPath;
import mil.tron.commonapi.dto.ScratchStorageAppRegistryDto;
import mil.tron.commonapi.dto.ScratchStorageAppUserPrivDto;
import mil.tron.commonapi.dto.ScratchStorageEntryDto;
import mil.tron.commonapi.dto.ScratchStorageUserDto;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.scratch.ScratchStorageAppRegistryEntry;
import mil.tron.commonapi.entity.scratch.ScratchStorageAppUserPriv;
import mil.tron.commonapi.entity.scratch.ScratchStorageEntry;
import mil.tron.commonapi.entity.scratch.ScratchStorageUser;
import mil.tron.commonapi.exception.InvalidFieldValueException;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.exception.scratch.InvalidJsonPathQueryException;
import mil.tron.commonapi.repository.PrivilegeRepository;
import mil.tron.commonapi.repository.scratch.ScratchStorageAppRegistryEntryRepository;
import mil.tron.commonapi.repository.scratch.ScratchStorageAppUserPrivRepository;
import mil.tron.commonapi.repository.scratch.ScratchStorageRepository;
import mil.tron.commonapi.repository.scratch.ScratchStorageUserRepository;
import org.assertj.core.util.Sets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;

@ExtendWith(MockitoExtension.class)
public class ScratchStorageServiceImplTest {

    @Mock
    private ScratchStorageRepository repository;

    @Mock
    private ScratchStorageAppRegistryEntryRepository appRegistryRepo;

    @Mock
    private PrivilegeRepository privRepo;

    @Mock
    private ScratchStorageAppUserPrivRepository appPrivRepo;

    @Mock
    private ScratchStorageUserRepository scratchUserRepo;

    @InjectMocks
    private ScratchStorageServiceImpl service;

    private Privilege privRead = Privilege
            .builder()
            .id(10L)
            .name("SCRATCH_READ")
            .build();

    private Privilege privWrite = Privilege
            .builder()
            .id(11L)
            .name("SCRATCH_WRITE")
            .build();

    private Privilege privAdmin = Privilege
            .builder()
            .id(12L)
            .name("SCRATCH_ADMIN")
            .build();

    private List<ScratchStorageEntry> entries = new ArrayList<>();
    private List<ScratchStorageAppRegistryEntry> registeredApps = new ArrayList<>();
    private ModelMapper mapper = new ModelMapper();
    private ScratchStorageUser user1 = ScratchStorageUser
            .builder()
            .id(UUID.randomUUID())
            .email("john@test.com")
            .build();

    @BeforeEach
    void setup() {
        entries.add(ScratchStorageEntry
                .builder()
                .id(UUID.randomUUID())
                .appId(UUID.randomUUID())
                .key("hello")
                .value("world")
                .build());

        entries.add(ScratchStorageEntry
                .builder()
                .id(UUID.randomUUID())
                .appId(UUID.randomUUID())
                .key("some key")
                .value("value")
                .build());

        registeredApps.add(ScratchStorageAppRegistryEntry
                .builder()
                .id(UUID.randomUUID())
                .appName("Area51")
                .userPrivs(Sets.newLinkedHashSet(
                        ScratchStorageAppUserPriv
                            .builder()
                            .user(user1)
                            .privilege(privRead)
                            .build(),
                        ScratchStorageAppUserPriv
                            .builder()
                            .user(user1)
                            .privilege(privWrite)
                            .build()
                ))
                .build());

        registeredApps.add(ScratchStorageAppRegistryEntry
                .builder()
                .id(UUID.randomUUID())
                .appName("CoolApp")
                .userPrivs(Set.of(
                        ScratchStorageAppUserPriv
                                .builder()
                                .user(user1)
                                .privilege(privRead)
                                .build(),
                        ScratchStorageAppUserPriv
                                .builder()
                                .user(user1)
                                .privilege(privWrite)
                                .build()
                ))
                .build());

    }

    @Test
    void testGetAllEntries() {
        Mockito.when(repository.findAll()).thenReturn(entries);
        assertEquals(entries.stream().map(item -> mapper.map(item, ScratchStorageEntryDto.class)).collect(Collectors.toList()), service.getAllEntries());
    }

    @Test
    void testGetAllEntriesByApp() {
        Mockito.when(appRegistryRepo.existsById(Mockito.any(UUID.class))).thenReturn(true);
        Mockito.when(repository.findAllByAppId(entries.get(0).getAppId())).thenReturn(Lists.newArrayList(entries.get(0)));
        assertEquals(Lists.newArrayList(mapper.map(entries.get(0), ScratchStorageEntryDto.class)), service.getAllEntriesByApp(entries.get(0).getAppId()));
    }

    @Test
    void testGetAllKeysForApp() {
        Mockito.when(appRegistryRepo.existsById(Mockito.any(UUID.class))).thenReturn(true);
        Mockito.when(repository.findAllKeysForAppId(entries.get(0).getAppId())).thenReturn(Lists.newArrayList(entries.get(0).getKey()));
        assertEquals(Lists.newArrayList(entries.get(0).getKey()), service.getAllKeysForAppId(entries.get(0).getAppId()));
    }


    @Test
    void testGetSingleAppById() {
        Mockito.when(appRegistryRepo.findById(Mockito.any(UUID.class)))
                .thenReturn(Optional.of(registeredApps.get(0)))
                .thenThrow(new RecordNotFoundException("Record not found"));

        assertEquals(registeredApps.get(0).getId(), service.getRegisteredScratchApp(registeredApps.get(0).getId()).getId());
        assertThrows(RecordNotFoundException.class, () -> service.getRegisteredScratchApp(registeredApps.get(0).getId()));

    }

    @Test
    void testGetEntryById() {
        Mockito.when(repository.findById(entries.get(0).getId()))
                .thenReturn(Optional.of(entries.get(0)))
                .thenReturn(Optional.empty());

        assertEquals(mapper.map(entries.get(0), ScratchStorageEntryDto.class), service.getEntryById(entries.get(0).getId()));
        assertThrows(RecordNotFoundException.class, () -> service.getEntryById(entries.get(0).getId()));

    }

    @Test
    void testGetKeyValueByAppId() {
        Mockito.when(appRegistryRepo.existsById(Mockito.any(UUID.class))).thenReturn(true);
        Mockito.when(repository.findByAppIdAndKey(entries.get(0).getId(), "hello"))
                .thenReturn(Optional.of(entries.get(0)))
                .thenReturn(Optional.empty());

        assertEquals(mapper.map(entries.get(0), ScratchStorageEntryDto.class), service.getKeyValueEntryByAppId(entries.get(0).getId(), "hello"));
        assertThrows(RecordNotFoundException.class, () -> service.getKeyValueEntryByAppId(entries.get(0).getId(), "hello"));
    }
    
    @Test
    void testGetAppsByUser() {
    	Mockito.when(appRegistryRepo.findAllAppsWithUserEmail(user1.getEmail())).thenReturn(registeredApps);
    	
    	DtoMapper mapper = new DtoMapper();
        List<ScratchStorageAppRegistryDto> registeredAppsDtos = new ArrayList<>();
        for (ScratchStorageAppRegistryEntry entry : registeredApps) {
            registeredAppsDtos.add(mapper.map(entry, ScratchStorageAppRegistryDto.class));
        }
        
    	Iterable<ScratchStorageAppRegistryDto> retrievedApps = service.getAllScratchAppsContainingUser(user1.getEmail());

    	for (ScratchStorageAppRegistryDto dto : retrievedApps) {
            for (ScratchStorageAppRegistryDto.UserWithPrivs priv : dto.getUserPrivs()) {
                assertThat(priv.getEmailAddress()).isEqualToIgnoringCase(user1.getEmail());
            }
        }
    }

    @Test
    void testSetKeyValuePair() {
        Mockito.when(appRegistryRepo.existsById(Mockito.any(UUID.class))).thenReturn(true);
        Mockito.when(repository.findByAppIdAndKey(entries.get(0).getAppId(), entries.get(0).getKey()))
                .thenReturn(Optional.of(entries.get(0)))  // first time record exists
                .thenReturn(Optional.empty()); // next time it doesn't

        Mockito.when(repository.save(Mockito.any(ScratchStorageEntry.class))).then(returnsFirstArg());

        ScratchStorageEntryDto entry = service.setKeyValuePair(entries.get(0).getAppId(), entries.get(0).getKey(), "new value");
        assertEquals("new value", entry.getValue());

        ScratchStorageEntryDto newEntry = service.setKeyValuePair(entries.get(0).getAppId(), entries.get(0).getKey(), "new value");
        assertEquals("new value", entry.getValue());
    }

    @Test
    void testDeleteKeyValuePairByAppId() {
        Mockito.when(appRegistryRepo.existsById(Mockito.any(UUID.class))).thenReturn(true);
        Mockito.when(repository.findByAppIdAndKey(Mockito.any(UUID.class), Mockito.anyString()))
                .thenReturn(Optional.of(entries.get(0))) // return an item first call
                .thenReturn(Optional.empty());  // return not found second call

        Mockito.doNothing().when(repository).deleteByAppIdAndKey(Mockito.any(UUID.class), Mockito.anyString());

        assertEquals(mapper.map(entries.get(0), ScratchStorageEntryDto.class), service.deleteKeyValuePair(entries.get(0).getId(), entries.get(0).getKey()));
        assertThrows(RecordNotFoundException.class, () -> service.deleteKeyValuePair(entries.get(0).getId(), entries.get(0).getKey()));
    }

    @Test
    void testDeleteAllPairsByAppId() {
        Mockito.when(appRegistryRepo.existsById(Mockito.any(UUID.class))).thenReturn(true);
        Mockito.when(repository.findAllByAppId(Mockito.any(UUID.class))).thenReturn(entries);
        Mockito.doNothing().when(repository).deleteById(Mockito.any(UUID.class));

        assertEquals(entries
                .stream()
                .map(item -> mapper.map(item, ScratchStorageEntryDto.class))
                .collect(Collectors.toList())
                    , service.deleteAllKeyValuePairsForAppId(UUID.randomUUID()));
    }

    // test scratch space app registration services

    @Test
    void testGetAllRegisteredApps() {
        DtoMapper mapper = new DtoMapper();
        List<ScratchStorageAppRegistryDto> registeredAppsDtos = new ArrayList<>();
        for (ScratchStorageAppRegistryEntry entry : registeredApps) {
            registeredAppsDtos.add(mapper.map(entry, ScratchStorageAppRegistryDto.class));
        }

        Mockito.when(appRegistryRepo.findAll()).thenReturn(registeredApps);
        assertEquals(registeredAppsDtos.get(0).getId(), Lists.newArrayList(service.getAllRegisteredScratchApps()).get(0).getId());
    }

    @Test
    void testAddNewRegisteredApp() {
        Mockito.when(appRegistryRepo.save(Mockito.any(ScratchStorageAppRegistryEntry.class))).then(returnsFirstArg());
        Mockito.when(appRegistryRepo.existsById(Mockito.any(UUID.class)))
                .thenReturn(false)
                .thenReturn(true);

        ScratchStorageAppRegistryDto newEntry = ScratchStorageAppRegistryDto
                .builder()
                .id(null)
                .appName("TestApp")
                .build();

        assertNotNull(service.addNewScratchAppName(newEntry).getId());
        assertThrows(ResourceAlreadyExistsException.class, () -> service.addNewScratchAppName(newEntry));
    }

    @Test
    void testEditRegisteredAppEntry() {
        Mockito.when(appRegistryRepo.save(Mockito.any(ScratchStorageAppRegistryEntry.class))).then(returnsFirstArg());

        ScratchStorageAppRegistryEntry newEntry = ScratchStorageAppRegistryEntry
                .builder()
                .id(UUID.randomUUID())
                .appName("TestApp")
                .build();

        ScratchStorageAppRegistryDto newEntryDto = mapper.map(newEntry, ScratchStorageAppRegistryDto.class);

        Mockito.when(appRegistryRepo.findById(Mockito.any(UUID.class)))
                .thenThrow(new RecordNotFoundException("Not Found"))
                .thenReturn(Optional.of(newEntry));

        assertThrows(InvalidRecordUpdateRequest.class, () -> service.editExistingScratchAppEntry(UUID.randomUUID(), newEntryDto));
        assertThrows(RecordNotFoundException.class, () -> service.editExistingScratchAppEntry(newEntry.getId(), newEntryDto));
        assertEquals(newEntryDto, service.editExistingScratchAppEntry(newEntry.getId(), newEntryDto));
    }

    @Test
    void testDeleteRegisteredAppEntry() {
        Mockito.doNothing().when(appRegistryRepo).deleteById(Mockito.any(UUID.class));

        Mockito.when(appRegistryRepo.existsById(Mockito.any(UUID.class)))
                .thenReturn(true);

        ScratchStorageAppRegistryEntry newEntry = ScratchStorageAppRegistryEntry
                .builder()
                .id(UUID.randomUUID())
                .appName("TestApp")
                .build();

        ScratchStorageAppRegistryDto newDto = mapper.map(newEntry, ScratchStorageAppRegistryDto.class);

        Mockito.when(appRegistryRepo.findById(Mockito.any(UUID.class)))
                .thenReturn(Optional.of(newEntry))
                .thenReturn(Optional.empty());

        assertEquals(newDto, service.deleteScratchStorageApp(newEntry.getId()));
        assertThrows(RecordNotFoundException.class, () -> service.deleteScratchStorageApp(newEntry.getId()));
    }

    @Test
    void testAddUserPrivToApp() {

        ScratchStorageAppUserPriv priv = ScratchStorageAppUserPriv
                .builder()
                .id(UUID.randomUUID())
                .user(user1)
                .privilege(privRead)
                .build();

        ScratchStorageAppUserPrivDto dto = ScratchStorageAppUserPrivDto
                .builder()
                .id(UUID.randomUUID())
                .email(user1.getEmail())
                .privilegeId(privRead.getId())
                .build();

        ScratchStorageAppRegistryEntry newEntry = ScratchStorageAppRegistryEntry
                .builder()
                .id(UUID.randomUUID())
                .appName("TestApp")
                .build();

        Mockito.when(appPrivRepo.save(Mockito.any(ScratchStorageAppUserPriv.class)))
                .then(returnsFirstArg());

        Mockito.when(scratchUserRepo.findByEmailIgnoreCase(Mockito.anyString()))
                .thenReturn(Optional.of(user1));

        Mockito.when(privRepo.findById(Mockito.any(Long.class)))
                .thenReturn(Optional.of(privRead))
                .thenReturn(Optional.of(privWrite));

        Mockito.when(appRegistryRepo.save(Mockito.any(ScratchStorageAppRegistryEntry.class)))
                .then(returnsFirstArg());

        Mockito.when(appRegistryRepo.findById(Mockito.any(UUID.class)))
                .thenReturn(Optional.of(newEntry))
                .thenReturn(Optional.of(newEntry))
                .thenReturn(Optional.of(newEntry))
                .thenReturn(Optional.empty());

        assertEquals(1, service.addUserPrivToApp(newEntry.getId(), dto).getUserPrivs().size());

        // adding same user/priv combo fails (cause it already exists)
        assertThrows(ResourceAlreadyExistsException.class, () -> service.addUserPrivToApp(newEntry.getId(), dto).getUserPrivs().size());

        // now change the same priv id to have write vice read (upgrade from read to write), should be good to go
        dto.setPrivilegeId(privWrite.getId());
        assertEquals(1, service.addUserPrivToApp(newEntry.getId(), dto).getUserPrivs().size());

        assertThrows(RecordNotFoundException.class, () -> service.addUserPrivToApp(newEntry.getId(), dto));
    }

    @Test
    void testRemoveUserPrivToApp() {

        ScratchStorageAppUserPriv priv = ScratchStorageAppUserPriv
                .builder()
                .id(UUID.randomUUID())
                .user(user1)
                .privilege(privRead)
                .build();

        ScratchStorageAppRegistryEntry newEntry = ScratchStorageAppRegistryEntry
                .builder()
                .id(UUID.randomUUID())
                .appName("TestApp")
                .userPrivs(Sets.newHashSet(Lists.newArrayList(priv)))
                .build();

        ScratchStorageAppUserPrivDto dto = ScratchStorageAppUserPrivDto
                .builder()
                .id(priv.getId())
                .email(user1.getEmail())
                .privilegeId(privRead.getId())
                .build();

        Mockito.when(appPrivRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(priv));
        Mockito.doNothing().when(appPrivRepo).deleteById(Mockito.any(UUID.class));

        Mockito.when(appRegistryRepo.save(Mockito.any(ScratchStorageAppRegistryEntry.class))).then(returnsFirstArg());
        Mockito.when(appRegistryRepo.findById(Mockito.any(UUID.class)))
                .thenReturn(Optional.of(newEntry))
                .thenReturn(Optional.empty());

        assertEquals(0, service.removeUserPrivFromApp(newEntry.getId(), priv.getId()).getUserPrivs().size());
        assertThrows(RecordNotFoundException.class, () -> service.removeUserPrivFromApp(newEntry.getId(), priv.getId()));
    }

    // test out the scratch user functions

    @Test
    void testGetAllScratchUsers() {

        Mockito.when(scratchUserRepo.findAll()).thenReturn(Lists.newArrayList(user1));
        assertEquals(1, Lists.newArrayList(service.getAllScratchUsers()).size());
    }

    @Test
    void testAddNewScratchUser() {
        Mockito.when(scratchUserRepo.existsById(Mockito.any(UUID.class)))
                .thenReturn(false)
                .thenReturn(true);

        Mockito.when(scratchUserRepo.save(Mockito.any(ScratchStorageUser.class))).then(returnsFirstArg());

        ScratchStorageUserDto newUser = ScratchStorageUserDto
                .builder()
                .id(null)
                .email("john@test.com")
                .build();

        assertNotNull(service.addNewScratchUser(newUser).getId());
        assertThrows(ResourceAlreadyExistsException.class, () -> service.addNewScratchUser(newUser));

    }

    @Test
    void testEditScratchUser() {
        Mockito.when(scratchUserRepo.existsById(Mockito.any(UUID.class)))
                .thenReturn(true)
                .thenReturn(false);

        Mockito.when(scratchUserRepo.save(Mockito.any(ScratchStorageUser.class))).then(returnsFirstArg());

        ScratchStorageUserDto newUser = ScratchStorageUserDto
                .builder()
                .id(UUID.randomUUID())
                .email("john@test.com")
                .build();

        assertThrows(InvalidRecordUpdateRequest.class, () -> service.editScratchUser(UUID.randomUUID(), newUser));
        assertEquals(newUser.getId(), service.editScratchUser(newUser.getId(), newUser).getId());
        assertThrows(RecordNotFoundException.class, () -> service.editScratchUser(newUser.getId(), newUser));
    }

    @Test
    void testDeleteScratchUser() {
        Mockito.when(scratchUserRepo.findById(Mockito.any(UUID.class)))
                .thenReturn(Optional.of(user1))
                .thenReturn(Optional.empty());

        Mockito.doNothing().when(scratchUserRepo).deleteById(Mockito.any(UUID.class));

        assertEquals(user1.getId(), service.deleteScratchUser(user1.getId()).getId());
        assertThrows(RecordNotFoundException.class, () -> service.deleteScratchUser(user1.getId()));
    }

    @Test
    void testUserCanWriteToAppSpace() {
        // tests logic of the utility function

        Mockito.when(appRegistryRepo.findById(Mockito.any(UUID.class)))
                .thenReturn(Optional.of(registeredApps.get(0)))
                .thenReturn(Optional.of(registeredApps.get(0)))
                .thenReturn(Optional.of(registeredApps.get(0)))
                .thenReturn(Optional.empty()); // not valid on subsequent

        ScratchStorageUser adminUser = ScratchStorageUser
                .builder()
                .id(UUID.randomUUID())
                .email("admin@test.com")
                .build();

        ScratchStorageUser someOtherNonRegisteredUser = ScratchStorageUser
                .builder()
                .id(UUID.randomUUID())
                .email("dude@test.com")
                .build();

        // add the admin guy to the app with ADMIN privs -- admin users will have implicit write access
        registeredApps.get(0).addUserAndPriv(ScratchStorageAppUserPriv
                .builder()
                .user(adminUser)
                .privilege(privAdmin)
                .build());

        assertTrue(service.userCanWriteToAppId(registeredApps.get(0).getId(), adminUser.getEmail(), false, null));
        assertTrue(service.userCanWriteToAppId(registeredApps.get(0).getId(), user1.getEmail(), false, null));
        assertFalse(service.userCanWriteToAppId(registeredApps.get(0).getId(), someOtherNonRegisteredUser.getEmail(), false, null));
        assertThrows(RecordNotFoundException.class,
                () -> service.userCanWriteToAppId(registeredApps.get(0).getId(), someOtherNonRegisteredUser.getEmail(), false, null));
    }

    @Test
    void testUserCanReadFromAppSpace() {
        // tests logic of the read utility function

        Mockito.when(appRegistryRepo.findById(Mockito.any(UUID.class)))
                .thenReturn(Optional.of(registeredApps.get(0)))
                .thenReturn(Optional.of(registeredApps.get(0)))
                .thenReturn(Optional.of(registeredApps.get(0)))
                .thenReturn(Optional.of(registeredApps.get(0)))
                .thenReturn(Optional.empty()); // not valid on subsequent

        ScratchStorageUser adminUser = ScratchStorageUser
                .builder()
                .id(UUID.randomUUID())
                .email("admin@test.com")
                .build();

        ScratchStorageUser writeUser = ScratchStorageUser
                .builder()
                .id(UUID.randomUUID())
                .email("writer@test.com")
                .build();

        ScratchStorageUser someOtherNonRegisteredUser = ScratchStorageUser
                .builder()
                .id(UUID.randomUUID())
                .email("dude@test.com")
                .build();

        // add the admin guy to the app with ADMIN privs -- admin users will have implicit read access
        registeredApps.get(0).addUserAndPriv(ScratchStorageAppUserPriv
                .builder()
                .user(adminUser)
                .privilege(privAdmin)
                .build());

        // add the writer guy to the app with WRITE privs -- write users will have implicit read access too
        registeredApps.get(0).addUserAndPriv(ScratchStorageAppUserPriv
                .builder()
                .user(writeUser)
                .privilege(privWrite)
                .build());

        assertTrue(service.userCanReadFromAppId(registeredApps.get(0).getId(), adminUser.getEmail(), false, null));
        assertTrue(service.userCanReadFromAppId(registeredApps.get(0).getId(), writeUser.getEmail(), false, null));
        assertTrue(service.userCanReadFromAppId(registeredApps.get(0).getId(), user1.getEmail(), false, null));
        assertFalse(service.userCanReadFromAppId(registeredApps.get(0).getId(), someOtherNonRegisteredUser.getEmail(), false, null));
        assertThrows(RecordNotFoundException.class,
                () -> service.userCanReadFromAppId(registeredApps.get(0).getId(), someOtherNonRegisteredUser.getEmail(), false, null));
    }

    @Test
    void testAppImplicitReadSetting() {
        // tests logic of the implicit read utility function

        Mockito.when(appRegistryRepo.findById(Mockito.any(UUID.class)))
                .thenReturn(Optional.of(registeredApps.get(0)))
                .thenReturn(Optional.of(registeredApps.get(0)))
                .thenReturn(Optional.of(registeredApps.get(0)))
                .thenReturn(Optional.of(registeredApps.get(0)))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(registeredApps.get(0)));

        ScratchStorageUser adminUser = ScratchStorageUser
                .builder()
                .id(UUID.randomUUID())
                .email("admin@test.com")
                .build();

        ScratchStorageUser writeUser = ScratchStorageUser
                .builder()
                .id(UUID.randomUUID())
                .email("writer@test.com")
                .build();

        ScratchStorageUser someOtherNonRegisteredUser = ScratchStorageUser
                .builder()
                .id(UUID.randomUUID())
                .email("dude@test.com")
                .build();

        // add the admin guy to the app with ADMIN privs -- admin users will have implicit read access
        registeredApps.get(0).addUserAndPriv(ScratchStorageAppUserPriv
                .builder()
                .user(adminUser)
                .privilege(privAdmin)
                .build());

        // add the writer guy to the app with WRITE privs -- write users will have implicit read access too
        registeredApps.get(0).addUserAndPriv(ScratchStorageAppUserPriv
                .builder()
                .user(writeUser)
                .privilege(privWrite)
                .build());

        assertTrue(service.userCanReadFromAppId(registeredApps.get(0).getId(), adminUser.getEmail(), false, null));
        assertTrue(service.userCanReadFromAppId(registeredApps.get(0).getId(), writeUser.getEmail(), false, null));
        assertTrue(service.userCanReadFromAppId(registeredApps.get(0).getId(), user1.getEmail(), false, null));
        assertFalse(service.userCanReadFromAppId(registeredApps.get(0).getId(), someOtherNonRegisteredUser.getEmail(), false, null));
        assertThrows(RecordNotFoundException.class,
                () -> service.userCanReadFromAppId(registeredApps.get(0).getId(), someOtherNonRegisteredUser.getEmail(), false, null));

        // turn on implicit read
        registeredApps.get(0).setAppHasImplicitRead(true);
        assertTrue(service.userCanReadFromAppId(registeredApps.get(0).getId(), someOtherNonRegisteredUser.getEmail(), false, null));

        // turn off implicit read
        registeredApps.get(0).setAppHasImplicitRead(false);
        assertFalse(service.userCanReadFromAppId(registeredApps.get(0).getId(), someOtherNonRegisteredUser.getEmail(), false, null));
    }

    @Test
    void testUserHasAdminAccessToAppSpace() {
         // tests logic of the utility function

        Mockito.when(appRegistryRepo.findById(Mockito.any(UUID.class)))
                .thenReturn(Optional.of(registeredApps.get(0)))
                .thenReturn(Optional.of(registeredApps.get(0)))
                .thenReturn(Optional.of(registeredApps.get(0)))
                .thenReturn(Optional.empty()); // not valid on subsequent

        ScratchStorageUser adminUser = ScratchStorageUser
                .builder()
                .id(UUID.randomUUID())
                .email("admin@test.com")
                .build();

        ScratchStorageUser someOtherNonRegisteredUser = ScratchStorageUser
                .builder()
                .id(UUID.randomUUID())
                .email("dude@test.com")
                .build();

        // add the admin guy to the app with ADMIN privs
        registeredApps.get(0).addUserAndPriv(ScratchStorageAppUserPriv
                .builder()
                .user(adminUser)
                .privilege(privAdmin)
                .build());

        assertTrue(service.userHasAdminWithAppId(registeredApps.get(0).getId(), adminUser.getEmail()));
        assertFalse(service.userHasAdminWithAppId(registeredApps.get(0).getId(), user1.getEmail()));
        assertFalse(service.userHasAdminWithAppId(registeredApps.get(0).getId(), someOtherNonRegisteredUser.getEmail()));
        assertThrows(RecordNotFoundException.class,
                () -> service.userHasAdminWithAppId(registeredApps.get(0).getId(), someOtherNonRegisteredUser.getEmail()));

    }

    @Test
    void testGetKeyValueAsJson() {

        ScratchStorageEntry entry = ScratchStorageEntry.builder()
            .id(UUID.randomUUID())
            .key("hello")
            .value("{ \"name\": \"Dude\", \"skills\": [ \"coding\", \"math\" ] }")
            .build();

        ScratchStorageEntry invalidJsonEntry = ScratchStorageEntry.builder()
                .id(UUID.randomUUID())
                .key("hello")
                .value("{ \"name: \"Dude\", \"skills\": [ \"coding\", \"math\" ] }")
                .build();

        Mockito.when(appRegistryRepo.existsById(Mockito.any(UUID.class))).thenReturn(true);
        Mockito.when(repository.findByAppIdAndKey(Mockito.any(UUID.class), Mockito.anyString()))
                .thenReturn(Optional.of(entry))
                .thenReturn(Optional.of(entry))
                .thenReturn(Optional.of(entry))
                .thenReturn(Optional.of(entry))
                .thenReturn(Optional.of(entry))
                .thenReturn(Optional.of(invalidJsonEntry));

        String retVal = service.getKeyValueJson(entry.getId(), "hello", "$.name");
        assertEquals("\"Dude\"", retVal);

        retVal = service.getKeyValueJson(entry.getId(), "hello", "$.skills");
        assertEquals("[ \"coding\", \"math\" ]", retVal);

        retVal = service.getKeyValueJson(entry.getId(), "hello", "$.skills[?(@ == 'math')]");
        assertEquals("[ \"math\" ]", retVal);

        retVal = service.getKeyValueJson(entry.getId(), "hello", "$.skills[?(@ == 'driving')]");
        assertEquals("[ ]", retVal);

        assertThrows(RecordNotFoundException.class, () -> service.getKeyValueJson(entry.getId(), "hello", "$.age"));
        assertThrows(InvalidFieldValueException.class, () -> service.getKeyValueJson(entry.getId(), "hello", "$.age"));
    }

    @Test
    void testSetKeyValueAsJson() {
        ScratchStorageEntry entry = ScratchStorageEntry.builder()
                .id(UUID.randomUUID())
                .key("hello")
                .value("{ \"name\": \"Dude\", \"skills\": [ \"coding\", \"math\" ] }")
                .build();

        ScratchStorageEntry invalidJsonEntry = ScratchStorageEntry.builder()
                .id(UUID.randomUUID())
                .key("hello")
                .value("{ \"name: \"Dude\", \"skills\": [ \"coding\", \"math\" ] }")
                .build();

        final ScratchStorageEntry[] updatedValue = {null};

        Mockito.when(appRegistryRepo.existsById(Mockito.any(UUID.class))).thenReturn(true);
        Mockito.when(repository.findByAppIdAndKey(Mockito.any(UUID.class), Mockito.anyString()))
                .thenReturn(Optional.of(entry))
                .thenReturn(Optional.of(entry))
                .thenReturn(Optional.of(invalidJsonEntry));

        Mockito.when(repository.save(Mockito.any(ScratchStorageEntry.class)))
                .thenAnswer(invocationOnMock -> {
                    updatedValue[0] = invocationOnMock.getArgument(0);
                    return invocationOnMock.getArgument(0);
                });

        service.patchKeyValueJson(entry.getId(), "hello",  "John", "$.name");
        assertTrue(updatedValue[0].getValue().contains("John"));

        assertThrows(InvalidFieldValueException.class, () -> service.patchKeyValueJson(entry.getId(), "hello",  "John", "$.age"));
        assertThrows(InvalidFieldValueException.class, () -> service.patchKeyValueJson(entry.getId(), "hello",  "John", "$.name"));
    }

    // tests for JsonDB

    @Test
    void testAddElement() {

        UUID appId = UUID.randomUUID();

        String jsonValue = "[{ \"id\": \"97031086-58a2-4228-8fa6-6d6544c1102d\", \"age\": 40, \"name\": \"Frank\", \"email\": \"f@test.com\" }]";
        String schema = "{ \"id\": \"uuid\", \"age\": \"number\", \"name\": \"string\", \"email\": \"email!*\" }";


        ScratchStorageEntry invalidJson = ScratchStorageEntry.builder()
                .id(UUID.randomUUID())
                .key("table")
                .value("{f}")
                .build();

        ScratchStorageEntry entry = ScratchStorageEntry.builder()
                .id(UUID.randomUUID())
                .key("table")
                .value(jsonValue)
                .build();

        ScratchStorageEntry schemaEntry = ScratchStorageEntry.builder()
                .id(UUID.randomUUID())
                .key("table_schema")
                .value(schema)
                .build();

        Mockito.when(repository.save(Mockito.any())).then(returnsFirstArg());

        Mockito.when(repository.findByAppIdAndKey(appId, "table_schema"))
                .thenReturn(Optional.ofNullable(schemaEntry));

        Mockito.when(repository.findByAppIdAndKey(appId, "table"))
                .thenThrow(new RecordNotFoundException("Not Found"))
                .thenReturn(Optional.ofNullable(invalidJson))
                .thenReturn(Optional.ofNullable(entry));

        assertThrows(RecordNotFoundException.class, () -> service.addElement(appId, "table", "{}"));
        assertThrows(InvalidJsonPathQueryException.class, ()-> service.addElement(appId, "table", "{}"));

        // make sure age is populated to default for type number -> which is 0
        service.addElement(appId, "table", "{ \"id\": \"97031086-58a2-4228-8fa6-6d6544c1102e\", \"name\": \"John\", \"email\": \"J@test.com\" }");
        List<Map<String, Object>> result = JsonPath.read(entry.getValue(), "$[?(@.age == 0)]");
        assertEquals(1, result.size());

        // make sure id is auto generated as a UUID
        service.addElement(appId, "table", "{ \"name\": \"Chris\", \"age\": 50, \"email\": \"c@test.com\" }");
        List<Map<String, Object>> idResult = JsonPath.read(entry.getValue(), "$[?(@.age == 50)]");
        assertDoesNotThrow(()-> UUID.fromString(idResult.get(0).get("id").toString()));

        // make sure the uniqueness check works
        assertThrows(Exception.class,
                () -> service.addElement(appId, "table", "{ \"id\": \"97031086-58a2-4228-8fa6-6d6544c1102e\", \"name\": \"John\", \"email\": \"J@test.com\" }"));
    }

    @Test
    void testUpdateElement() {

        UUID appId = UUID.randomUUID();

        String jsonValue = "[{ \"id\": \"97031086-58a2-4228-8fa6-6d6544c1102d\", \"age\": 40, \"name\": \"Frank\", \"email\": \"f@test.com\" }]";
        String schema = "{ \"id\": \"uuid\", \"age\": \"number\", \"name\": \"string\", \"email\": \"email!*\" }";


        ScratchStorageEntry invalidJson = ScratchStorageEntry.builder()
                .id(UUID.randomUUID())
                .key("table")
                .value("{f}")
                .build();

        ScratchStorageEntry entry = ScratchStorageEntry.builder()
                .id(UUID.randomUUID())
                .key("table")
                .value(jsonValue)
                .build();

        ScratchStorageEntry schemaEntry = ScratchStorageEntry.builder()
                .id(UUID.randomUUID())
                .key("table_schema")
                .value(schema)
                .build();

        Mockito.when(repository.save(Mockito.any())).then(returnsFirstArg());

        Mockito.when(repository.findByAppIdAndKey(appId, "table_schema"))
                .thenReturn(Optional.ofNullable(schemaEntry));

        Mockito.when(repository.findByAppIdAndKey(appId, "table"))
                .thenThrow(new RecordNotFoundException("Not Found"))
                .thenReturn(Optional.ofNullable(invalidJson))
                .thenReturn(Optional.ofNullable(entry));

        assertThrows(RecordNotFoundException.class, () -> service.updateElement(appId, "table", "{}", "$"));
        assertThrows(InvalidJsonPathQueryException.class, ()-> service.updateElement(appId, "table", "{}", "$"));

        // make sure we can update the existing record with given UUID, and we change the name from John to Juan
        //  and verify
        service.updateElement(appId,
                "table",
                "{ \"id\": \"97031086-58a2-4228-8fa6-6d6544c1102d\", \"name\": \"Juan\", \"email\": \"f@test.com\" }",
                "$[?(@.id == '97031086-58a2-4228-8fa6-6d6544c1102d')]");

        List<Map<String, Object>> result = JsonPath.read(entry.getValue(), "$[?(@.id == '97031086-58a2-4228-8fa6-6d6544c1102d')]");
        assertEquals("Juan", result.get(0).get("name"));

        // make sure we throw error when update-able target does not exist
        assertThrows(RecordNotFoundException.class, () -> service.updateElement(appId,
                "table",
                "{ \"id\": \"97031086-58a2-4228-8fa6-6d6544c1102f\", \"name\": \"Juan\", \"email\": \"J@test.com\" }",
                "$[?(@.id == '97031086-58a2-4228-8fa6-6d6544c1102e')]"));


    }

    @Test
    void testRemoveElement() {

        UUID appId = UUID.randomUUID();

        String jsonValue = "[ " +
                "{ \"id\": \"97031086-58a2-4228-8fa6-6d6544c1102d\", \"age\": 40, \"name\": \"Frank\", \"email\": \"f@test.com\" }, " +
                "{ \"id\": \"97031086-58a2-4228-8fa6-6d6544c1102e\", \"age\": 41, \"name\": \"Bill\", \"email\": \"b@test.com\" } " +
                "]";
        String schema = "{ \"id\": \"uuid\", \"age\": \"number\", \"name\": \"string\", \"email\": \"email!*\" }";


        ScratchStorageEntry invalidJson = ScratchStorageEntry.builder()
                .id(UUID.randomUUID())
                .key("table")
                .value("{f}")
                .build();

        ScratchStorageEntry entry = ScratchStorageEntry.builder()
                .id(UUID.randomUUID())
                .key("table")
                .value(jsonValue)
                .build();

        ScratchStorageEntry schemaEntry = ScratchStorageEntry.builder()
                .id(UUID.randomUUID())
                .key("table_schema")
                .value(schema)
                .build();

        Mockito.when(repository.save(Mockito.any())).then(returnsFirstArg());

        Mockito.when(repository.findByAppIdAndKey(appId, "table"))
                .thenThrow(new RecordNotFoundException("Not Found"))
                .thenReturn(Optional.ofNullable(invalidJson))
                .thenReturn(Optional.ofNullable(entry));

        assertThrows(RecordNotFoundException.class, () -> service.removeElement(appId, "table", "{}"));
        assertThrows(InvalidJsonPathQueryException.class, ()-> service.removeElement(appId, "table", "{}"));

        // make sure we can remove an existing record and verify
        service.removeElement(appId,
                "table",
                "$[?(@.id == '97031086-58a2-4228-8fa6-6d6544c1102d')]");

        List<Map<String, Object>> result = JsonPath.read(entry.getValue(), "$[?(@.id == '97031086-58a2-4228-8fa6-6d6544c1102d')]");
        assertEquals(0, result.size());

        // make sure we throw error when record doesn't exist
        assertThrows(RecordNotFoundException.class, () -> service.removeElement(appId,
                "table",
                "$[?(@.id == '97031086-58a2-4228-8fa6-6d6544c1102d')]"));


    }

    @Test
    void testQueryElement() throws JsonProcessingException {

        UUID appId = UUID.randomUUID();

        String jsonValue = "[ " +
                "{ \"id\": \"97031086-58a2-4228-8fa6-6d6544c1102d\", \"age\": 40, \"name\": \"Frank\", \"email\": \"f@test.com\" }, " +
                "{ \"id\": \"97031086-58a2-4228-8fa6-6d6544c1102e\", \"age\": 41, \"name\": \"Bill\", \"email\": \"b@test.com\" } " +
                "]";
        String schema = "{ \"id\": \"uuid\", \"age\": \"number\", \"name\": \"string\", \"email\": \"email!*\" }";


        ScratchStorageEntry invalidJson = ScratchStorageEntry.builder()
                .id(UUID.randomUUID())
                .key("table")
                .value("{f}")
                .build();

        ScratchStorageEntry entry = ScratchStorageEntry.builder()
                .id(UUID.randomUUID())
                .key("table")
                .value(jsonValue)
                .build();

        Mockito.when(repository.findByAppIdAndKey(appId, "table"))
                .thenThrow(new RecordNotFoundException("Not Found"))
                .thenReturn(Optional.ofNullable(invalidJson))
                .thenReturn(Optional.ofNullable(entry));

        assertThrows(RecordNotFoundException.class, () -> service.queryJson(appId, "table", "{}"));
        assertThrows(InvalidJsonPathQueryException.class, ()-> service.queryJson(appId, "table", "{}"));

        // make sure we can query records
        Object result = service.queryJson(appId,
                "table",
                "$[?(@.id == '97031086-58a2-4228-8fa6-6d6544c1102d')]");
        assertEquals(1,  (new ObjectMapper().readValue(result.toString(), Object[].class)).length);

        // make sure we can query records by getting fancy with regex
        result = service.queryJson(appId,
                "table",
                "$[?(@.email =~ /.*test.com/i)]");
        assertEquals(2, (new ObjectMapper().readValue(result.toString(), Object[].class)).length);

        assertThrows(RecordNotFoundException.class, () -> service.queryJson(appId, "table",
                "$[?(@.email =~ /.*test.mil/i)]"));

    }
}
