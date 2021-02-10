package mil.tron.commonapi.service.scratch;


import com.google.common.collect.Lists;
import mil.tron.commonapi.entity.scratch.ScratchStorageEntry;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.scratch.ScratchStorageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;

@ExtendWith(MockitoExtension.class)
public class ScratchStorageServiceImplTest {

    @Mock
    private ScratchStorageRepository repository;

    @InjectMocks
    private ScratchStorageServiceImpl service;

    private List<ScratchStorageEntry> entries = new ArrayList<>();

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
    }

    @Test
    void testGetAllEntries() {
        Mockito.when(repository.findAll()).thenReturn(entries);
        assertEquals(entries, service.getAllEntries());
    }

    @Test
    void testGetAllEntriesByApp() {
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
        Mockito.when(repository.findByAppIdAndKey(entries.get(0).getId(), "hello"))
                .thenReturn(Optional.of(entries.get(0)))
                .thenReturn(Optional.empty());

        assertEquals(entries.get(0), service.getKeyValueEntryByAppId(entries.get(0).getId(), "hello"));
        assertThrows(RecordNotFoundException.class, () -> service.getKeyValueEntryByAppId(entries.get(0).getId(), "hello"));
    }

    @Test
    void testSetKeyValuePair() {
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
        Mockito.when(repository.findByAppIdAndKey(Mockito.any(UUID.class), Mockito.anyString()))
                .thenReturn(Optional.of(entries.get(0))) // return an item first call
                .thenReturn(Optional.empty());  // return not found second call

        Mockito.doNothing().when(repository).deleteByAppIdAndKey(Mockito.any(UUID.class), Mockito.anyString());

        assertEquals(entries.get(0), service.deleteKeyValuePair(entries.get(0).getId(), entries.get(0).getKey()));
        assertThrows(RecordNotFoundException.class, () -> service.deleteKeyValuePair(entries.get(0).getId(), entries.get(0).getKey()));
    }

    @Test
    void testDeleteAllPairsByAppId() {
        Mockito.when(repository.findAllByAppId(Mockito.any(UUID.class))).thenReturn(entries);
        Mockito.doNothing().when(repository).deleteById(Mockito.any(UUID.class));

        assertEquals(entries, service.deleteAllKeyValuePairsForAppId(UUID.randomUUID()));
    }
}
