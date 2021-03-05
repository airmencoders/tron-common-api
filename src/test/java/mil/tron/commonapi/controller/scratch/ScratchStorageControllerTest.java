package mil.tron.commonapi.controller.scratch;

import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.dto.PrivilegeDto;
import mil.tron.commonapi.dto.ScratchStorageAppRegistryDto;
import mil.tron.commonapi.dto.ScratchStorageAppUserPrivDto;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.scratch.ScratchStorageAppRegistryEntry;
import mil.tron.commonapi.entity.scratch.ScratchStorageAppUserPriv;
import mil.tron.commonapi.entity.scratch.ScratchStorageEntry;
import mil.tron.commonapi.entity.scratch.ScratchStorageUser;
import mil.tron.commonapi.service.PrivilegeService;
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
    private static final String SCRATCH_USERS_ENDPOINT = "/v1/scratch/users/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScratchStorageService service;

    @MockBean
    private PrivilegeService privService;

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
                .user(user1)
                .privilege(privRead)
                .build());

        // user1 can WRITE from the Area51 space
        registeredAppsUserPrivs.add(ScratchStorageAppUserPriv
                .builder()
                .id(UUID.randomUUID())
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

        Mockito.when(service.userCanWriteToAppId(Mockito.any(UUID.class), Mockito.anyString()))
                .thenReturn(true); // let user have the privs to mutate

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

        Mockito.when(service.userCanWriteToAppId(Mockito.any(UUID.class), Mockito.anyString()))
                .thenReturn(true); // let user have the privs to mutate

        Mockito.when(service.deleteAllKeyValuePairsForAppId(entries.get(0).getAppId())).thenReturn(Lists.newArrayList(entries.get(0)));

        mockMvc.perform(delete(ENDPOINT + "{appId}", entries.get(0).getAppId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].appId", equalTo(entries.get(0).getAppId().toString())));
    }

    @Test
    void testDeleteAllByAppIdAndKeyName() throws Exception {
        // test we can delete a specific key value pair for an app given its ID


        Mockito.when(service.userCanWriteToAppId(Mockito.any(UUID.class), Mockito.anyString()))
                .thenReturn(true) // let user have the privs to mutate
                .thenReturn(false);   // deny on second call

        Mockito.when(service.deleteKeyValuePair(entries.get(0).getAppId(), "hello")).thenReturn(entries.get(0));

        mockMvc.perform(delete(ENDPOINT + "{appId}/{keyName}", entries.get(0).getAppId(), entries.get(0).getKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId", equalTo(entries.get(0).getAppId().toString())));

        mockMvc.perform(delete(ENDPOINT + "{appId}/{keyName}", entries.get(0).getAppId(), entries.get(0).getKey()))
                .andExpect(status().isForbidden());

    }

    //
    // test out the app registration endpoints

    @Test
    void testGetAllRegisteredApps() throws Exception {
        // test we can get all registered apps

        DtoMapper mapper = new DtoMapper();
        List<ScratchStorageAppRegistryDto> dtos = new ArrayList<>();
        for (ScratchStorageAppRegistryEntry entry : registeredApps) {
            dtos.add(mapper.map(registeredApps, ScratchStorageAppRegistryDto.class));
        }

        Mockito.when(service.getAllRegisteredScratchApps()).thenReturn(dtos);


        mockMvc.perform(get(SCRATCH_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void testGetAppsRecordById() throws Exception {
        // test we can get a single app's record by its ID

        DtoMapper mapper = new DtoMapper();
        ScratchStorageAppRegistryDto dto = mapper.map(registeredApps.get(0), ScratchStorageAppRegistryDto.class);
        Mockito.when(service.getRegisteredScratchApp(Mockito.any(UUID.class))).thenReturn(dto);

        Mockito.when(service.userHasAdminWithAppId(Mockito.any(UUID.class), Mockito.anyString())).thenReturn(true);

        mockMvc.perform(get(SCRATCH_ENDPOINT + "{appId}", registeredApps.get(0).getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(dto.getId().toString())));

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

    @Test
    void testAddingUserPrivToApp() throws Exception {

        ScratchStorageAppRegistryEntry newEntry = ScratchStorageAppRegistryEntry
                .builder()
                .id(UUID.randomUUID())
                .appName("TestApp")
                .build();

        ScratchStorageAppUserPrivDto priv = ScratchStorageAppUserPrivDto
                .builder()
                .id(UUID.randomUUID())
                .email("test@test.net")
                .privilegeId(1L)
                .build();

        Mockito.when(service.addUserPrivToApp(Mockito.any(UUID.class), Mockito.any(ScratchStorageAppUserPrivDto.class)))
                .thenReturn(newEntry);

        Mockito.when(service.userHasAdminWithAppId(Mockito.any(UUID.class), Mockito.anyString()))
                .thenReturn(false)
                .thenReturn(true);

        mockMvc.perform(patch(SCRATCH_ENDPOINT + "{appId}/user", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(priv)))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch(SCRATCH_ENDPOINT + "{appId}/user", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(priv)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(newEntry.getId().toString())));

    }

    @Test
    void testRemovingUserPrivFromApp() throws Exception {

        ScratchStorageAppRegistryEntry newEntry = ScratchStorageAppRegistryEntry
                .builder()
                .id(UUID.randomUUID())
                .appName("TestApp")
                .build();

        ScratchStorageAppUserPriv priv = ScratchStorageAppUserPriv
                .builder()
                .id(UUID.randomUUID())
                .build();

        Mockito.when(service.removeUserPrivFromApp(Mockito.any(UUID.class), Mockito.any(UUID.class)))
                .thenReturn(newEntry);

        Mockito.when(service.userHasAdminWithAppId(Mockito.any(UUID.class), Mockito.anyString()))
                .thenReturn(false)
                .thenReturn(true);

        mockMvc.perform(delete(SCRATCH_ENDPOINT + "{appId}/user/{privId}", UUID.randomUUID(), UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(priv)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete(SCRATCH_ENDPOINT + "{appId}/user/{privId}", UUID.randomUUID(), UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(priv)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(newEntry.getId().toString())));

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

    @Test
    void testGetScratchPrivs() throws Exception {
        // test fetching scratch privileges, controller will return only privs with /^SCRATCH_/


        PrivilegeDto dto1 = new PrivilegeDto();
        dto1.setId(1L);
        dto1.setName("WRITE");

        PrivilegeDto dto2 = new PrivilegeDto();
        dto2.setId(2L);
        dto2.setName("SCRATCH_WRITE");

        Mockito.when(privService.getPrivileges())
                .thenReturn(Lists.newArrayList(dto1, dto2));

        mockMvc.perform(get("/v1/scratch/users/privs"))
                .andExpect(status().isOk())
                .andExpect(result -> jsonPath("$", hasSize(1)));
    }
}
