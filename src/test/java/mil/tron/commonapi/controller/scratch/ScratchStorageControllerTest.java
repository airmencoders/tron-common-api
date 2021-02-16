package mil.tron.commonapi.controller.scratch;

import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.scratch.ScratchStorageAppRegistryEntry;
import mil.tron.commonapi.entity.scratch.ScratchStorageAppUserPriv;
import mil.tron.commonapi.entity.scratch.ScratchStorageEntry;
import mil.tron.commonapi.entity.scratch.ScratchStorageUser;
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
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.AdditionalAnswers.returnsSecondArg;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScratchStorageController.class)
public class ScratchStorageControllerTest {

    private static final String ENDPOINT = "/v1/scratch/";
    private static final String SCRATCH_ENDPOINT = "/v1/scratch/apps/";
    private static final String SCRATCH_PRIVS_ENDPOINT = "/v1/scratch/privs/";
    private static final String SCRATCH_USERS_ENDPOINT = "/v1/scratch/users/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScratchStorageService service;

    private List<ScratchStorageEntry> entries = new ArrayList<>();
    private List<ScratchStorageAppRegistryEntry> registeredApps = new ArrayList<>();
    private List<ScratchStorageAppUserPriv> registeredAppsUserPrivs = new ArrayList<>();

    private ScratchStorageUser user1 = ScratchStorageUser
            .builder()
            .id(UUID.randomUUID())
            .email("john@test.com")
            .build();

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

        registeredApps.add(ScratchStorageAppRegistryEntry
                .builder()
                .id(UUID.randomUUID())
                .appName("Area51")
                .build());

        registeredApps.add(ScratchStorageAppRegistryEntry
                .builder()
                .id(UUID.randomUUID())
                .appName("CoolApp")
                .build());

        // user1 can READ from the Area51 space
        registeredAppsUserPrivs.add(ScratchStorageAppUserPriv
                .builder()
                .id(UUID.randomUUID())
                .app(registeredApps.get(0))
                .user(user1)
                .privilege(privRead)
                .build());

        // user1 can WRITE from the Area51 space
        registeredAppsUserPrivs.add(ScratchStorageAppUserPriv
                .builder()
                .id(UUID.randomUUID())
                .app(registeredApps.get(0))
                .user(user1)
                .privilege(privWrite)
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

    // test out the app registration endpoints

    @Test
    void testGetAllRegisteredApps() throws Exception {
        // test we can get all registered apps

        Mockito.when(service.getAllRegisteredScratchApps()).thenReturn(registeredApps);

        mockMvc.perform(get(SCRATCH_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void testAddNewRegisteredApp() throws Exception {
        // test we can add a new app

        Mockito.when(service.addNewScratchAppName(Mockito.any(ScratchStorageAppRegistryEntry.class)))
                .then(returnsFirstArg());

        ScratchStorageAppRegistryEntry newEntry = ScratchStorageAppRegistryEntry
                .builder()
                .id(null)
                .appName("TestApp")
                .build();

        mockMvc.perform(post(SCRATCH_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(newEntry)))
                .andExpect(status().isCreated())
                .andExpect(result ->
                        assertEquals(newEntry.getAppName(), OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), ScratchStorageAppRegistryEntry.class).getAppName()));
    }

    @Test
    void testEditRegisteredApp() throws Exception {
        // test we can edit an existing app entry

        Mockito.when(service.editExistingScratchAppEntry(Mockito.any(UUID.class), Mockito.any(ScratchStorageAppRegistryEntry.class)))
                .then(returnsSecondArg());

        ScratchStorageAppRegistryEntry newEntry = ScratchStorageAppRegistryEntry
                .builder()
                .id(UUID.randomUUID())
                .appName("TestApp")
                .build();

        mockMvc.perform(put(SCRATCH_ENDPOINT + "{id}", newEntry.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(newEntry)))
                .andExpect(status().isOk())
                .andExpect(result ->
                        assertEquals(newEntry.getAppName(), OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), ScratchStorageAppRegistryEntry.class).getAppName()));
    }

    @Test
    void testDeleteRegisteredApp() throws Exception {
        // test we can delete a registered app

        ScratchStorageAppRegistryEntry newEntry = ScratchStorageAppRegistryEntry
                .builder()
                .id(UUID.randomUUID())
                .appName("TestApp")
                .build();

        Mockito.when(service.deleteScratchStorageApp(Mockito.any(UUID.class))).thenReturn(newEntry);

        mockMvc.perform(delete(SCRATCH_ENDPOINT + "{id}", newEntry.getId()))
                .andExpect(status().isOk())
                .andExpect(result ->
                        assertEquals(newEntry.getAppName(), OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), ScratchStorageAppRegistryEntry.class).getAppName()));
    }

    // test out the app-user-priv endpoints

    @Test
    void testGetAllAppUserPrivsList() throws Exception {
        // test we can get all of the registered app-user privs table

        Mockito.when(service.getAllAppsToUsersPrivs()).thenReturn(registeredAppsUserPrivs);
        mockMvc.perform(get(SCRATCH_PRIVS_ENDPOINT)).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void testGetAllPrivsByAppId() throws Exception {
        // test we can get all the privs for a given app id
        Mockito.when(service.getAllPrivsForAppId(Mockito.any(UUID.class))).thenReturn(Lists.newArrayList(registeredAppsUserPrivs.get(0)));
        mockMvc.perform(get(SCRATCH_PRIVS_ENDPOINT + "{id}", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void testAddNewAppUserPriv() throws Exception {
        // test we can add a new App-User priv entry

        ScratchStorageAppUserPriv entry = ScratchStorageAppUserPriv
                .builder()
                .id(UUID.randomUUID())
                .app(registeredApps.get(0))
                .user(user1)
                .privilege(privRead)
                .build();

        Mockito.when(service.addNewUserAppPrivilegeEntry(Mockito.any(ScratchStorageAppUserPriv.class)))
                .then(returnsFirstArg());

        mockMvc.perform(post(SCRATCH_PRIVS_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(entry)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", equalTo(entry.getId().toString())));
    }

    @Test
    void testEditAppUserPriv() throws Exception {
        // test we can edit an existing app-user priv entry

        ScratchStorageAppUserPriv entry = ScratchStorageAppUserPriv
                .builder()
                .id(UUID.randomUUID())
                .app(registeredApps.get(0))
                .user(user1)
                .privilege(privRead)
                .build();

        Mockito.when(service.editUserAppPrivilegeEntry(Mockito.any(UUID.class), Mockito.any(ScratchStorageAppUserPriv.class)))
                .then(returnsSecondArg());

        mockMvc.perform(put(SCRATCH_PRIVS_ENDPOINT + "{id}", entry.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(entry)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(entry.getId().toString())));
    }

    @Test
    void testDeleteAppUserPriv() throws Exception {
        // test we can delete an app-user priv

        ScratchStorageAppUserPriv entry = ScratchStorageAppUserPriv
                .builder()
                .id(UUID.randomUUID())
                .app(registeredApps.get(0))
                .user(user1)
                .privilege(privRead)
                .build();

        Mockito.when(service.deleteUserAppPrivilege(Mockito.any(UUID.class)))
                .thenReturn(entry);

        mockMvc.perform(delete(SCRATCH_PRIVS_ENDPOINT + "{id}", entry.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(entry.getId().toString())));
    }

    // test out the scratch user management endpoints

    @Test
    void testGetAllScratchUsers() throws Exception {
        // test we can get all the scratch users

        Mockito.when(service.getAllScratchUsers()).thenReturn(Lists.newArrayList(user1));
        mockMvc.perform(get(SCRATCH_USERS_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void testAddNewScratchUser() throws Exception {
        // test we can add a new user to the scratch users so they can get a UUID

        Mockito.when(service.addNewScratchUser(Mockito.any(ScratchStorageUser.class))).thenReturn(user1);
        mockMvc.perform(post(SCRATCH_USERS_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(user1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", equalTo(user1.getId().toString())));
    }

    @Test
    void testEditScratchUser() throws Exception {
        // test we can edit a user to the scratch user

        Mockito.when(service.editScratchUser(Mockito.any(UUID.class), Mockito.any(ScratchStorageUser.class)))
                .then(returnsSecondArg());

        mockMvc.perform(put(SCRATCH_USERS_ENDPOINT + "{id}", user1.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(user1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(user1.getId().toString())));
    }

    @Test
    void testDeleteScratchUser() throws Exception {
        // test we can delete a scratch user

        Mockito.when(service.deleteScratchUser(Mockito.any(UUID.class))).thenReturn(user1);

        mockMvc.perform(delete(SCRATCH_USERS_ENDPOINT + "{id}", user1.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(user1.getId().toString())));
    }
}
