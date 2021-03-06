package mil.tron.commonapi.service.scratch;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.jayway.jsonpath.JsonPath;
import mil.tron.commonapi.dto.*;
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
import mil.tron.commonapi.service.PersonConversionOptions;
import org.assertj.core.util.Sets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

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
        Mockito.when(appRegistryRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(registeredApps.get(0)));
        Mockito.when(repository.findAllByAppId(entries.get(0).getAppId())).thenReturn(Lists.newArrayList(entries.get(0)));
        assertEquals(Lists.newArrayList(mapper.map(entries.get(0), ScratchStorageEntryDto.class)), service.getAllEntriesByApp(entries.get(0).getAppId()));
    }

    @Test
    void testGetAllKeysForApp() {
        Mockito.when(appRegistryRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(registeredApps.get(0)));
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
        Mockito.when(appRegistryRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(registeredApps.get(0)));
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
        Mockito.when(appRegistryRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(registeredApps.get(0)));
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
        Mockito.when(appRegistryRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(registeredApps.get(0)));
        Mockito.when(repository.findByAppIdAndKey(Mockito.any(UUID.class), Mockito.anyString()))
                .thenReturn(Optional.of(entries.get(0))) // return an item first call
                .thenReturn(Optional.empty());  // return not found second call

        doNothing().when(repository).deleteByAppIdAndKey(Mockito.any(UUID.class), Mockito.anyString());

        assertEquals(mapper.map(entries.get(0), ScratchStorageEntryDto.class), service.deleteKeyValuePair(entries.get(0).getId(), entries.get(0).getKey()));
        assertThrows(RecordNotFoundException.class, () -> service.deleteKeyValuePair(entries.get(0).getId(), entries.get(0).getKey()));
    }

    @Test
    void testDeleteAllPairsByAppId() {
        Mockito.when(appRegistryRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(registeredApps.get(0)));
        Mockito.when(repository.findAllByAppId(Mockito.any(UUID.class))).thenReturn(entries);
        doNothing().when(repository).deleteById(Mockito.any(UUID.class));

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
        Mockito.when(appRegistryRepo.saveAndFlush(Mockito.any(ScratchStorageAppRegistryEntry.class))).then(returnsFirstArg());
        doNothing().when(appPrivRepo).deleteById(Mockito.any(UUID.class));

        ScratchStorageUser user = ScratchStorageUser.builder()
                .email("test@test.com")
                .build();

        Privilege p = Privilege.builder()
                .id(7L)
                .name("SCRATCH_ADMIN")
                .build();

        ScratchStorageAppUserPriv priv = ScratchStorageAppUserPriv.builder()
                .user(user)
                .privilege(p)
                .build();

        ScratchStorageAppRegistryEntry newEntry = ScratchStorageAppRegistryEntry
                .builder()
                .id(UUID.randomUUID())
                .appName("TestApp")
                .appHasImplicitRead(false)
                .aclMode(false)
                .build();

        newEntry.addUserAndPriv(priv);

        ScratchStorageAppRegistryDto newEntryDto = mapper.map(newEntry, ScratchStorageAppRegistryDto.class);
        Mockito.when(appRegistryRepo.getOne(Mockito.any(UUID.class))).thenReturn(newEntry);

        Mockito.when(appRegistryRepo.findById(Mockito.any(UUID.class)))
                .thenThrow(new RecordNotFoundException("Not Found"))
                .thenReturn(Optional.of(newEntry));

        assertThrows(InvalidRecordUpdateRequest.class, () -> service.editExistingScratchAppEntry(UUID.randomUUID(), newEntryDto));
        assertThrows(RecordNotFoundException.class, () -> service.editExistingScratchAppEntry(newEntry.getId(), newEntryDto));

        ScratchStorageAppRegistryDto modEntry = ScratchStorageAppRegistryDto
                .builder()
                .id(newEntry.getId())
                .appName("TestApp2")
                .appHasImplicitRead(true)
                .aclMode(true)
                .build();

        ScratchStorageAppRegistryDto updatedDto = service.editExistingScratchAppEntry(newEntry.getId(), modEntry);
        assertEquals(newEntryDto.getId(), updatedDto.getId());
        assertEquals("TestApp2", updatedDto.getAppName());
        assertTrue(updatedDto.isAclMode());
        assertTrue(updatedDto.isAppHasImplicitRead());
    }

    @Test
    void testDeleteRegisteredAppEntry() {
        doNothing().when(appRegistryRepo).deleteById(Mockito.any(UUID.class));
        ScratchStorageAppRegistryEntry newEntry = ScratchStorageAppRegistryEntry
                .builder()
                .id(UUID.randomUUID())
                .appName("TestApp")
                .build();

        ScratchStorageAppRegistryDto newDto = mapper.map(newEntry, ScratchStorageAppRegistryDto.class);

        Mockito.when(appRegistryRepo.findById(Mockito.any(UUID.class)))
                .thenReturn(Optional.of(newEntry))
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
        doNothing().when(appPrivRepo).deleteById(Mockito.any(UUID.class));

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

        doNothing().when(scratchUserRepo).deleteById(Mockito.any(UUID.class));

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

        assertTrue(service.userCanWriteToAppId(registeredApps.get(0).getId(), adminUser.getEmail(), null));
        assertTrue(service.userCanWriteToAppId(registeredApps.get(0).getId(), user1.getEmail(), null));
        assertFalse(service.userCanWriteToAppId(registeredApps.get(0).getId(), someOtherNonRegisteredUser.getEmail(), null));
        assertThrows(RecordNotFoundException.class,
                () -> service.userCanWriteToAppId(registeredApps.get(0).getId(), someOtherNonRegisteredUser.getEmail(), null));
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

        assertTrue(service.userCanReadFromAppId(registeredApps.get(0).getId(), adminUser.getEmail(), null));
        assertTrue(service.userCanReadFromAppId(registeredApps.get(0).getId(), writeUser.getEmail(), null));
        assertTrue(service.userCanReadFromAppId(registeredApps.get(0).getId(), user1.getEmail(), null));
        assertFalse(service.userCanReadFromAppId(registeredApps.get(0).getId(), someOtherNonRegisteredUser.getEmail(), null));
        assertThrows(RecordNotFoundException.class,
                () -> service.userCanReadFromAppId(registeredApps.get(0).getId(), someOtherNonRegisteredUser.getEmail(), null));
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

        assertTrue(service.userCanReadFromAppId(registeredApps.get(0).getId(), adminUser.getEmail(), null));
        assertTrue(service.userCanReadFromAppId(registeredApps.get(0).getId(), writeUser.getEmail(), null));
        assertTrue(service.userCanReadFromAppId(registeredApps.get(0).getId(), user1.getEmail(), null));
        assertFalse(service.userCanReadFromAppId(registeredApps.get(0).getId(), someOtherNonRegisteredUser.getEmail(), null));
        assertThrows(RecordNotFoundException.class,
                () -> service.userCanReadFromAppId(registeredApps.get(0).getId(), someOtherNonRegisteredUser.getEmail(), null));

        // turn on implicit read
        registeredApps.get(0).setAppHasImplicitRead(true);
        assertTrue(service.userCanReadFromAppId(registeredApps.get(0).getId(), someOtherNonRegisteredUser.getEmail(), null));

        // turn off implicit read
        registeredApps.get(0).setAppHasImplicitRead(false);
        assertFalse(service.userCanReadFromAppId(registeredApps.get(0).getId(), someOtherNonRegisteredUser.getEmail(), null));
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
    void testGetKeysUserCanRead() {
        UUID id = UUID.randomUUID();

        Mockito.when(appRegistryRepo.findById(id))
                .thenReturn(Optional.of(ScratchStorageAppRegistryEntry
                        .builder()
                        .id(id)
                        .appName("CoolAppWithAcl")
                        .aclMode(true)
                        .userPrivs(Set.of(
                            ScratchStorageAppUserPriv
                                .builder()
                                .user(ScratchStorageUser.builder().email("admin@test1.com").build())
                                .privilege(privAdmin)
                                .build()
                        ))
                        .build()));

        Mockito.when(repository.findAllKeysForAppId(id))
                .thenReturn(Lists.newArrayList("Test", "Test1", "Test_acl", "Test1_acl"));

        Mockito.when(repository.findByAppIdAndKey(id, "Test_acl")).thenReturn(Optional.of(
                        ScratchStorageEntry.builder()
                            .id(UUID.randomUUID())
                            .appId(id)
                            .key("Test_acl")
                            .value(" { \"implicitRead\": true, \"access\": { \"john@test.com\": \"KEY_READ\" }}")
                            .build()));

        Mockito.when(repository.findByAppIdAndKey(id, "Test1_acl")).thenReturn(Optional.of(
                ScratchStorageEntry.builder()
                        .id(UUID.randomUUID())
                        .appId(id)
                        .key("Test1_acl")
                        .value(" { \"implicitRead\": false, \"access\": { \"john@test.com\": \"KEY_READ\" }}")
                        .build()));

        assertEquals(4, service.getKeysUserCanReadFrom(id, "admin@test1.com").size());
        assertEquals(2, service.getKeysUserCanReadFrom(id, "john@test.com").size());
        assertEquals(1, service.getKeysUserCanReadFrom(id, "random_person@test.com").size());
    }

    @Test
    void testGetKeysUserCanWriteTo() {
        UUID id = UUID.randomUUID();

        Mockito.when(appRegistryRepo.findById(id))
                .thenReturn(Optional.of(ScratchStorageAppRegistryEntry
                        .builder()
                        .id(id)
                        .appName("CoolAppWithAcl")
                        .aclMode(true)
                        .userPrivs(Set.of(
                                ScratchStorageAppUserPriv
                                        .builder()
                                        .user(ScratchStorageUser.builder().email("admin@test1.com").build())
                                        .privilege(privAdmin)
                                        .build()
                        ))
                        .build()));

        Mockito.when(repository.findAllKeysForAppId(id))
                .thenReturn(Lists.newArrayList("Test", "Test1", "Test_acl", "Test1_acl"));

        Mockito.when(repository.findByAppIdAndKey(id, "Test_acl")).thenReturn(Optional.of(
                ScratchStorageEntry.builder()
                        .id(UUID.randomUUID())
                        .appId(id)
                        .key("Test_acl")
                        .value(" { \"implicitRead\": true, \"access\": { \"john@test.com\": \"KEY_READ\", \"frank@test.com\": \"KEY_ADMIN\", \"william@test.com\": \"KEY_WRITE\" }}")
                        .build()));

        Mockito.when(repository.findByAppIdAndKey(id, "Test1_acl")).thenReturn(Optional.of(
                ScratchStorageEntry.builder()
                        .id(UUID.randomUUID())
                        .appId(id)
                        .key("Test1_acl")
                        .value(" { \"implicitRead\": false, \"access\": { \"john@test.com\": \"KEY_READ\" }}")
                        .build()));

        assertEquals(4, service.getKeysUserCanWriteTo(id, "admin@test1.com").size());  // SCRATCH_ADMIN is everything everytime
        assertEquals(2, service.getKeysUserCanWriteTo(id, "frank@test.com").size());  // KEY_ADMIN is only admin of applicable keys AND their acls
        assertEquals(1, service.getKeysUserCanWriteTo(id, "william@test.com").size());  // KEY_WRITE is just bound to keys with WRITE and below
        assertEquals(0, service.getKeysUserCanWriteTo(id, "random_person@test.com").size());
    }

    @Test
    void testGetKeysUserCanAdmin() {
        UUID id = UUID.randomUUID();

        Mockito.when(appRegistryRepo.findById(id))
                .thenReturn(Optional.of(ScratchStorageAppRegistryEntry
                        .builder()
                        .id(id)
                        .appName("CoolAppWithAcl")
                        .aclMode(true)
                        .userPrivs(Set.of(
                                ScratchStorageAppUserPriv
                                        .builder()
                                        .user(ScratchStorageUser.builder().email("admin@test1.com").build())
                                        .privilege(privAdmin)
                                        .build()
                        ))
                        .build()));

        Mockito.when(repository.findAllKeysForAppId(id))
                .thenReturn(Lists.newArrayList("Test", "Test1", "Test_acl", "Test1_acl"));

        Mockito.when(repository.findByAppIdAndKey(id, "Test_acl")).thenReturn(Optional.of(
                ScratchStorageEntry.builder()
                        .id(UUID.randomUUID())
                        .appId(id)
                        .key("Test_acl")
                        .value(" { \"implicitRead\": true, \"access\": { \"john@test.com\": \"KEY_READ\", \"frank@test.com\": \"KEY_ADMIN\", \"william@test.com\": \"KEY_WRITE\" }}")
                        .build()));

        Mockito.when(repository.findByAppIdAndKey(id, "Test1_acl")).thenReturn(Optional.of(
                ScratchStorageEntry.builder()
                        .id(UUID.randomUUID())
                        .appId(id)
                        .key("Test1_acl")
                        .value(" { \"implicitRead\": false, \"access\": { \"john@test.com\": \"KEY_READ\" }}")
                        .build()));

        assertEquals(4, service.getKeysUserIsAdmin(id, "admin@test1.com").size());  // SCRATCH_ADMIN is everything everytime
        assertEquals(2, service.getKeysUserIsAdmin(id, "frank@test.com").size());  // KEY_ADMIN is only admin of applicable keys AND their acls
        assertEquals(0, service.getKeysUserIsAdmin(id, "william@test.com").size());  // KEY_WRITE is just bound to keys with WRITE and below
        assertEquals(0, service.getKeysUserIsAdmin(id, "random_person@test.com").size());
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

        Mockito.when(appRegistryRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(registeredApps.get(0)));
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

        Mockito.when(appRegistryRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(registeredApps.get(0)));
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

    @Test
    void testAddToJsonValue() {

        // tests adding a key/value (field/value) to a json structure using json path

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

        Mockito.when(appRegistryRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(registeredApps.get(0)));
        Mockito.when(repository.findByAppIdAndKey(Mockito.any(UUID.class), Mockito.anyString()))
                .thenReturn(Optional.of(entry))
                .thenReturn(Optional.of(entry))
                .thenReturn(Optional.of(invalidJsonEntry));

        Mockito.when(repository.save(Mockito.any(ScratchStorageEntry.class)))
                .thenAnswer(invocationOnMock -> {
                    updatedValue[0] = invocationOnMock.getArgument(0);
                    return invocationOnMock.getArgument(0);
                });

        service.addKeyValueJson(entry.getId(), "hello",  "age", "38", "$");
        assertTrue(updatedValue[0].getValue().contains("age"));

        assertThrows(InvalidFieldValueException.class, () -> service.addKeyValueJson(entry.getId(), "hello",  "age", "38", "$"));
        assertThrows(InvalidFieldValueException.class, () -> service.addKeyValueJson(entry.getId(), "hello",  "age", "38", "$"));
    }

    @Test
    void testDeleteFromJsonValue() {

        // tests deleting a key/value (field/value) from a json structure using json path

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

        Mockito.when(appRegistryRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(registeredApps.get(0)));
        Mockito.when(repository.findByAppIdAndKey(Mockito.any(UUID.class), Mockito.anyString()))
                .thenReturn(Optional.of(entry))
                .thenReturn(Optional.of(entry))
                .thenReturn(Optional.of(invalidJsonEntry));

        Mockito.when(repository.save(Mockito.any(ScratchStorageEntry.class)))
                .thenAnswer(invocationOnMock -> {
                    updatedValue[0] = invocationOnMock.getArgument(0);
                    return invocationOnMock.getArgument(0);
                });

        service.deleteKeyValueJson(entry.getId(), "hello",  "$.name");
        assertFalse(updatedValue[0].getValue().contains("name"));

        assertThrows(InvalidFieldValueException.class, () -> service.deleteKeyValueJson(entry.getId(), "hello", "$.name"));
        assertThrows(InvalidFieldValueException.class, () -> service.deleteKeyValueJson(entry.getId(), "hello",  "$.name"));
    }

    @Test
    void testAclModeSettingToggle() {

        // test that placing a scratch app into and out of ACL Mode
        ScratchStorageAppRegistryEntry testApp = registeredApps.get(0);
        assertFalse(testApp.isAclMode());

        Mockito.when(appRegistryRepo.findById(Mockito.any())).thenReturn(Optional.of(testApp));

        Mockito.when(appRegistryRepo.save(Mockito.any())).then(returnsFirstArg());

        ScratchStorageAppRegistryDto app = service.setAclModeForApp(testApp.getId(), true);
        assertTrue(app.isAclMode());

        app = service.setAclModeForApp(testApp.getId(), false);
        assertFalse(app.isAclMode());
    }

    @Test
    void testUserCanReadFromAppInAclMode() {

        ScratchStorageAppRegistryEntry testApp = registeredApps.get(0);
        testApp.setAclMode(true);

        Mockito.when(appRegistryRepo.findById(Mockito.any())).thenReturn(Optional.of(testApp));
        Mockito.when(repository.findByAppIdAndKey(testApp.getId(), "users_acl"))
                // no ACL found
                .thenReturn(Optional.empty())

                // invalid json
                .thenReturn(Optional.of(ScratchStorageEntry
                        .builder()
                        .key("users_acl")
                        .value("{ ")
                        .build()))

                // valid json but missing fields
                .thenReturn(Optional.of(ScratchStorageEntry
                        .builder()
                        .key("users_acl")
                        .value("{ \"users\" : { \"access\" : { \"test@test.com\": \"KEY_READ\" } }}")
                        .build()))

                // valid json but missing fields
                .thenReturn(Optional.of(ScratchStorageEntry
                        .builder()
                        .key("users_acl")
                        .value("{ \"implicitRead\" : false, \"access\" : [ \"test@test.com\" ] }")
                        .build()))

                // valid json but missing fields
                .thenReturn(Optional.of(ScratchStorageEntry
                        .builder()
                        .key("users_acl")
                        .value("{ \"implicitRead\" : false, \"roles\" : { \"test@test.com\": \"KEY_READ\" } }")
                        .build()))

                // valid json but unknown permissions
                .thenReturn(Optional.of(ScratchStorageEntry
                        .builder()
                        .key("users_acl")
                        .value("{ \"implicitRead\" : false, \"access\" : { \"test@test.com\": \"SUPERADMIN\" } }")
                        .build()))

                // valid json
                .thenReturn(Optional.of(ScratchStorageEntry
                        .builder()
                        .key("users_acl")
                        .value("{ \"implicitRead\" : false, \"access\" : { \"test@test.com\": \"KEY_READ\" } }")
                        .build()));

        assertThrows(RecordNotFoundException.class, () -> service.userCanReadFromAppId(testApp.getId(), "test@test.com",  "users"));
        assertThrows(InvalidFieldValueException.class, () -> service.userCanReadFromAppId(testApp.getId(), "test@test.com", "users"));
        assertThrows(InvalidFieldValueException.class, () -> service.userCanReadFromAppId(testApp.getId(), "test@test.com", "users"));
        assertThrows(InvalidFieldValueException.class, () -> service.userCanReadFromAppId(testApp.getId(), "test@test.com", "users"));
        assertThrows(InvalidFieldValueException.class, () -> service.userCanReadFromAppId(testApp.getId(), "test@test.com", "users"));
        assertFalse(service.userCanWriteToAppId(testApp.getId(), "test@test.com", "users"));
        assertTrue(service.userCanReadFromAppId(testApp.getId(), "test@test.com", "users"));
    }

    @Test
    void testUserCanWriteToAppInAclMode() {

        ScratchStorageAppRegistryEntry testApp = registeredApps.get(0);
        testApp.setAclMode(true);

        Mockito.when(appRegistryRepo.findById(Mockito.any())).thenReturn(Optional.of(testApp));
        Mockito.when(repository.findByAppIdAndKey(testApp.getId(), "users_acl"))
                .thenReturn(Optional.of(ScratchStorageEntry
                        .builder()
                        .key("users_acl")
                        .value("{ \"implicitRead\" : false, \"access\" : { \"test@test.com\": \"KEY_WRITE\" } }")
                        .build()));

        assertTrue(service.userCanWriteToAppId(testApp.getId(), "test@test.com", "users"));
        assertFalse(service.userCanWriteToAppId(testApp.getId(), "test@test.com", "users_acl"));
        assertFalse(service.userCanWriteToAppId(testApp.getId(), "dude@test.com", "users"));
        assertTrue(service.userCanReadFromAppId(testApp.getId(), "test@test.com", "users"));
    }


}
