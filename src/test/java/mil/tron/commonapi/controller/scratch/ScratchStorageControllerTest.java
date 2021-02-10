package mil.tron.commonapi.controller.scratch;

import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.entity.scratch.ScratchStorageEntry;
import mil.tron.commonapi.service.scratch.ScratchStorageService;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScratchStorageController.class)
public class ScratchStorageControllerTest {

    private static final String ENDPOINT = "/v1/scratch/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScratchStorageService service;

    private List<ScratchStorageEntry> entries = new ArrayList<>();

    @BeforeEach
    void setup() {
        entries.add(ScratchStorageEntry
                .builder()
                .appId(UUID.randomUUID())
                .key("hello")
                .value("world")
                .build());

        entries.add(ScratchStorageEntry
                .builder()
                .appId(UUID.randomUUID())
                .key("some key")
                .value("value")
                .build());
    }

    @Test
    void testGetAllKeyValuePairs() throws Exception {

        Mockito.when(service.getAllEntries()).thenReturn(entries);
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void testGetAllByAppId() throws Exception {
        // test we can get all entries belonging to an App ID UUID

        UUID appId = entries.get(0).getAppId();
        Mockito.when(service.getAllEntriesByApp(appId)).thenReturn(Lists.newArrayList(entries.get(0)));
        mockMvc.perform(get(ENDPOINT  +"{appId}", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].appId", equalTo(appId.toString())));
    }

    @Test
    void testGetByAppIdAndKeyValue() throws Exception {
        // test we can get a discrete entry by its key name within a given app id

        UUID appId = entries.get(0).getAppId();
        String keyValue = "hello";
        Mockito.when(service.getKeyValueEntryByAppId(appId, keyValue)).thenReturn(entries.get(0));
        mockMvc.perform(get(ENDPOINT  + "{appId}/{keyValue}", appId, keyValue))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId", equalTo(appId.toString())))
                .andExpect(jsonPath("$.value", equalTo(entries.get(0).getValue())));

        appId = entries.get(1).getAppId();
        keyValue = "some key";
        Mockito.when(service.getKeyValueEntryByAppId(appId, keyValue)).thenReturn(entries.get(1));
        mockMvc.perform(get(ENDPOINT  + "{appId}/{keyValue}", appId, keyValue))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId", equalTo(appId.toString())))
                .andExpect(jsonPath("$.value", equalTo(entries.get(1).getValue())));
    }

    @Test
    void testCreateUpdateKeyValuePair() throws Exception {
        // test we can add/update at key value pair (just like a real hash would be)

        ScratchStorageEntry entry = ScratchStorageEntry
                .builder()
                .id(UUID.randomUUID())
                .appId(UUID.randomUUID())
                .key("name")
                .value("Chris")
                .build();

        Mockito.when(service.setKeyValuePair(entry.getAppId(), entry.getKey(), entry.getValue())).thenReturn(entry);

        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(entry)))
                .andExpect(status().isOk())
                .andExpect(result ->
                        assertEquals(entry.getValue(), OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), ScratchStorageEntry.class).getValue()));

    }

    @Test
    void testDeleteAllByAppId() throws Exception {
        // test we can delete all key value pairs for an app given its ID

        Mockito.when(service.deleteAllKeyValuePairsForAppId(entries.get(0).getAppId())).thenReturn(Lists.newArrayList(entries.get(0)));

        mockMvc.perform(delete(ENDPOINT + "{appId}", entries.get(0).getAppId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].appId", equalTo(entries.get(0).getAppId().toString())));
    }

    @Test
    void testDeleteAllByAppIdAndKeyName() throws Exception {
        // test we can delete a specific key value pair for an app given its ID

        Mockito.when(service.deleteKeyValuePair(entries.get(0).getAppId(), "hello")).thenReturn(entries.get(0));

        mockMvc.perform(delete(ENDPOINT + "{appId}/{keyName}", entries.get(0).getAppId(), entries.get(0).getKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId", equalTo(entries.get(0).getAppId().toString())));
    }
}
