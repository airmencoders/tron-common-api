package mil.tron.commonapi.service.scratch;


import com.google.common.collect.Lists;
import mil.tron.commonapi.dto.ScratchStorageAppUserPrivDto;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.scratch.ScratchStorageAppRegistryEntry;
import mil.tron.commonapi.entity.scratch.ScratchStorageAppUserPriv;
import mil.tron.commonapi.entity.scratch.ScratchStorageEntry;
import mil.tron.commonapi.entity.scratch.ScratchStorageUser;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
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

import java.util.*;

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
            .name("READ")
            .build();

    private Privilege privWrite = Privilege
            .builder()
            .id(11L)
            .name("WRITE")
            .build();

    private List<ScratchStorageEntry> entries = new ArrayList<>();
    private List<ScratchStorageAppRegistryEntry> registeredApps = new ArrayList<>();

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
        assertEquals(entries, service.getAllEntries());
    }

    @Test
    void testGetAllEntriesByApp() {
        Mockito.when(appRegistryRepo.existsById(Mockito.any(UUID.class))).thenReturn(true);
        Mockito.when(repository.findAllByAppId(entries.get(0).getAppId())).thenReturn(Lists.newArrayList(entries.get(0)));
        assertEquals(Lists.newArrayList(entries.get(0)), service.getAllEntriesByApp(entries.get(0).getAppId()));
    }

    @Test
    void testGetEntryById() {
        Mockito.when(repository.findById(entries.get(0).getId()))
                .thenReturn(Optional.of(entries.get(0)))
                .thenReturn(Optional.empty());

        assertEquals(entries.get(0), service.getEntryById(entries.get(0).getId()));
        assertThrows(RecordNotFoundException.class, () -> service.getEntryById(entries.get(0).getId()));

        // test bogus Id

    }

    @Test
    void testGetKeyValueByAppId() {
        Mockito.when(appRegistryRepo.existsById(Mockito.any(UUID.class))).thenReturn(true);
        Mockito.when(repository.findByAppIdAndKey(entries.get(0).getId(), "hello"))
                .thenReturn(Optional.of(entries.get(0)))
                .thenReturn(Optional.empty());

        assertEquals(entries.get(0), service.getKeyValueEntryByAppId(entries.get(0).getId(), "hello"));
        assertThrows(RecordNotFoundException.class, () -> service.getKeyValueEntryByAppId(entries.get(0).getId(), "hello"));
    }

    @Test
    void testSetKeyValuePair() {
        Mockito.when(appRegistryRepo.existsById(Mockito.any(UUID.class))).thenReturn(true);
        Mockito.when(repository.findByAppIdAndKey(entries.get(0).getAppId(), entries.get(0).getKey()))
                .thenReturn(Optional.of(entries.get(0)))  // first time record exists
                .thenReturn(Optional.empty()); // next time it doesn't

        Mockito.when(repository.save(Mockito.any(ScratchStorageEntry.class))).then(returnsFirstArg());

        ScratchStorageEntry entry = service.setKeyValuePair(entries.get(0).getAppId(), entries.get(0).getKey(), "new value");
        assertEquals("new value", entry.getValue());

        ScratchStorageEntry newEntry = service.setKeyValuePair(entries.get(0).getAppId(), entries.get(0).getKey(), "new value");
        assertEquals("new value", entry.getValue());
    }

    @Test
    void testDeleteKeyValuePairByAppId() {
        Mockito.when(appRegistryRepo.existsById(Mockito.any(UUID.class))).thenReturn(true);
        Mockito.when(repository.findByAppIdAndKey(Mockito.any(UUID.class), Mockito.anyString()))
                .thenReturn(Optional.of(entries.get(0))) // return an item first call
                .thenReturn(Optional.empty());  // return not found second call

        Mockito.doNothing().when(repository).deleteByAppIdAndKey(Mockito.any(UUID.class), Mockito.anyString());

        assertEquals(entries.get(0), service.deleteKeyValuePair(entries.get(0).getId(), entries.get(0).getKey()));
        assertThrows(RecordNotFoundException.class, () -> service.deleteKeyValuePair(entries.get(0).getId(), entries.get(0).getKey()));
    }

    @Test
    void testDeleteAllPairsByAppId() {
        Mockito.when(appRegistryRepo.existsById(Mockito.any(UUID.class))).thenReturn(true);
        Mockito.when(repository.findAllByAppId(Mockito.any(UUID.class))).thenReturn(entries);
        Mockito.doNothing().when(repository).deleteById(Mockito.any(UUID.class));

        assertEquals(entries, service.deleteAllKeyValuePairsForAppId(UUID.randomUUID()));
    }

    // test scratch space app registration services

    @Test
    void testGetAllRegisteredApps() {
        Mockito.when(appRegistryRepo.findAll()).thenReturn(registeredApps);
        assertEquals(registeredApps, service.getAllRegisteredScratchApps());
    }

    @Test
    void testAddNewRegisteredApp() {
        Mockito.when(appRegistryRepo.save(Mockito.any(ScratchStorageAppRegistryEntry.class))).then(returnsFirstArg());
        Mockito.when(appRegistryRepo.existsById(Mockito.any(UUID.class)))
                .thenReturn(false)
                .thenReturn(true);

        ScratchStorageAppRegistryEntry newEntry = ScratchStorageAppRegistryEntry
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

        Mockito.when(appRegistryRepo.existsById(Mockito.any(UUID.class)))
                .thenReturn(false)
                .thenReturn(true);

        assertThrows(InvalidRecordUpdateRequest.class, () -> service.editExistingScratchAppEntry(UUID.randomUUID(), newEntry));
        assertThrows(RecordNotFoundException.class, () -> service.editExistingScratchAppEntry(newEntry.getId(), newEntry));
        assertEquals(newEntry, service.editExistingScratchAppEntry(newEntry.getId(), newEntry));
    }

    @Test
    void testDeleteRegisteredAppEntry() {
        Mockito.doNothing().when(appRegistryRepo).deleteById(Mockito.any(UUID.class));

        ScratchStorageAppRegistryEntry newEntry = ScratchStorageAppRegistryEntry
                .builder()
                .id(UUID.randomUUID())
                .appName("TestApp")
                .build();

        Mockito.when(appRegistryRepo.findById(Mockito.any(UUID.class)))
                .thenReturn(Optional.of(newEntry))
                .thenReturn(Optional.empty());

        assertEquals(newEntry, service.deleteScratchStorageApp(newEntry.getId()));
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
                .userId(UUID.randomUUID())
                .privilegeId(1L)
                .build();

        ScratchStorageAppRegistryEntry newEntry = ScratchStorageAppRegistryEntry
                .builder()
                .id(UUID.randomUUID())
                .appName("TestApp")
                .build();

        Mockito.when(appPrivRepo.save(Mockito.any(ScratchStorageAppUserPriv.class))).then(returnsFirstArg());
        Mockito.when(scratchUserRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(user1));
        Mockito.when(privRepo.findById(Mockito.any(Long.class))).thenReturn(Optional.of(privRead));

        Mockito.when(appRegistryRepo.save(Mockito.any(ScratchStorageAppRegistryEntry.class))).then(returnsFirstArg());

        Mockito.when(appRegistryRepo.findById(Mockito.any(UUID.class)))
                .thenReturn(Optional.of(newEntry))
                .thenReturn(Optional.empty());

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
                .userId(user1.getId())
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

        ScratchStorageUser newUser = ScratchStorageUser
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

        ScratchStorageUser newUser = ScratchStorageUser
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
}
