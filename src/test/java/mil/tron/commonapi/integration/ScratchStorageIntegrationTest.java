package mil.tron.commonapi.integration;


import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.entity.scratch.ScratchStorageEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import javax.transaction.Transactional;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * Full stack test from controller to H2 for scratch space
 */

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
public class ScratchStorageIntegrationTest {

    private static final String ENDPOINT = "/v1/scratch/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    private ScratchStorageEntry entry1 = ScratchStorageEntry
            .builder()
            .appId(UUID.randomUUID())
            .key("hello")
            .value("world")
            .build();

    private ScratchStorageEntry entry2 = ScratchStorageEntry
            .builder()
            .appId(UUID.randomUUID())
            .key("some key")
            .value("value")
            .build();

    @BeforeEach
    void setup() throws Exception {

        ScratchStorageEntry entry3 = ScratchStorageEntry
                .builder()
                .appId(entry2.getAppId())
                .key("some key2")
                .value("value")
                .build();

        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(entry2)))
                .andExpect(status().isOk());

        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(entry3)))
                .andExpect(status().isOk());
    }

    @Transactional
    @Rollback
    @Test
    void testInvalidAppID() throws Exception {

        ScratchStorageEntry entry = ScratchStorageEntry
                .builder()
                .appId(null)
                .key("some key2")
                .value("value")
                .build();

        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(entry)))
                .andExpect(status().isBadRequest());
    }

    @Transactional
    @Rollback
    @Test
    void testInvalidKeyValue() throws Exception {

        ScratchStorageEntry entry = ScratchStorageEntry
                .builder()
                .appId(UUID.randomUUID())
                .key(null)
                .value("value")
                .build();

        // null key not allowed
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(entry)))
                .andExpect(status().isBadRequest());

        entry.setKey("");

        // blank key not allowed
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(entry)))
                .andExpect(status().isBadRequest());

    }

    @Transactional
    @Rollback
    @Test
    void testAddKeyValuePair() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(entry1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId", equalTo(entry1.getAppId().toString())));
    }

    @Transactional
    @Rollback
    @Test
    void getAllKeyValuePairs() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(entry1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Transactional
    @Rollback
    @Test
    void getAllKeyValuePairsByAppId() throws Exception {
        mockMvc.perform(get(ENDPOINT + "{appId}", entry2.getAppId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Transactional
    @Rollback
    @Test
    void getKeyValuePairByAppIdAndKeyName() throws Exception {
        mockMvc.perform(get(ENDPOINT + "{appId}/{keyName}", entry2.getAppId(), entry2.getKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value", equalTo(entry2.getValue())));

        // test key doesnt exist
        mockMvc.perform(get(ENDPOINT + "{appId}/{keyName}", entry2.getAppId(), "bogus key"))
                .andExpect(status().isNotFound());
    }

    @Transactional
    @Rollback
    @Test
    void deleteKeyValuePairsByAppId() throws Exception {

        // make total of 3 records in the db, 2 being of the same appId
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(entry1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId", equalTo(entry1.getAppId().toString())));

        mockMvc.perform(delete(ENDPOINT + "{appId}", entry2.getAppId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Transactional
    @Rollback
    @Test
    void deleteKeyValuePair() throws Exception {

        // make total of 3 records in the db, 2 being of the same appId
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(entry1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId", equalTo(entry1.getAppId().toString())));

        mockMvc.perform(delete(ENDPOINT + "{appId}/{keyValue}", entry2.getAppId(), entry2.getKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value", equalTo(entry2.getValue())));

        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // delete bogus key
        mockMvc.perform(delete(ENDPOINT + "{appId}/{keyValue}", entry2.getAppId(), "bogus key"))
                .andExpect(status().isNotFound());
    }

}
