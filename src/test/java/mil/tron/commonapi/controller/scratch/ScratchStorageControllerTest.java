package mil.tron.commonapi.controller.scratch;

import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.dto.*;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.scratch.ScratchStorageAppUserPriv;
import mil.tron.commonapi.entity.scratch.ScratchStorageEntry;
import mil.tron.commonapi.entity.scratch.ScratchStorageUser;
import mil.tron.commonapi.service.PrivilegeService;
import mil.tron.commonapi.service.scratch.ScratchStorageService;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.AdditionalAnswers.returnsSecondArg;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class ScratchStorageControllerTest {

    private static final String ENDPOINT = "/v1/scratch/";
    private static final String ENDPOINT_V2 = "/v2/scratch/";
    private static final String SCRATCH_ENDPOINT = "/v1/scratch/apps/";
    private static final String SCRATCH_ENDPOINT_V2 = "/v2/scratch/apps/";
    private static final String SCRATCH_USERS_ENDPOINT = "/v1/scratch/users/";
    private static final String SCRATCH_USERS_ENDPOINT_V2 = "/v2/scratch/users/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScratchStorageService service;

    @MockBean
    private PrivilegeService privService;

    private List<ScratchStorageEntryDto> entries = new ArrayList<>();
    private List<ScratchStorageAppRegistryDto> registeredApps = new ArrayList<>();
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
        entries.add(ScratchStorageEntryDto
                .builder()
                .appId(UUID.randomUUID())
                .key("hello")
                .value("world")
                .build());

        entries.add(ScratchStorageEntryDto
                .builder()
                .appId(UUID.randomUUID())
                .key("some key")
                .value("value")
                .build());

        registeredApps.add(ScratchStorageAppRegistryDto
                .builder()
                .id(UUID.randomUUID())
                .appName("Area51")
                .build());

        registeredApps.add(ScratchStorageAppRegistryDto
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
        
        // V2
        mockMvc.perform(get(ENDPOINT_V2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void testGetAllByAppId() throws Exception {
        // test we can get all entries belonging to an App ID UUID

        UUID appId = entries.get(0).getAppId();
        Mockito.when(service.userCanReadFromAppId(Mockito.any(UUID.class), Mockito.anyString(), Mockito.anyBoolean(), Mockito.anyString()))
                .thenReturn(true); // let user have the privs to read
        Mockito.when(service.getAllEntriesByApp(appId)).thenReturn(Lists.newArrayList(entries.get(0)));
        mockMvc.perform(get(ENDPOINT  +"{appId}", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].appId", equalTo(appId.toString())));
    }

    @Test
    void testSetImplicitReadByAppId() throws Exception {
        // test we can access the implicit read setter route
        UUID appId = entries.get(0).getAppId();
        Mockito.when(service.setImplicitReadForApp(Mockito.any(UUID.class), Mockito.anyBoolean()))
                .thenReturn(registeredApps.get(0));
        Mockito.when(service.userHasAdminWithAppId(Mockito.any(UUID.class), Mockito.anyString()))
                .thenReturn(true);

        mockMvc.perform(patch(ENDPOINT  +"apps/{appId}/implicitRead?value=true", appId))
                .andExpect(status().isOk());
    }


    @Test
    void testGetAllAppKeys() throws Exception {
        // test getting just the keys for a given app
        UUID appId = entries.get(0).getAppId();
        Mockito.when(service.userCanReadFromAppId(Mockito.any(UUID.class), Mockito.anyString(), Mockito.anyBoolean(), Mockito.anyString()))
                .thenReturn(true); // let user have the privs to read
        Mockito.when(service.getAllKeysForAppId(appId)).thenReturn(Lists.newArrayList(entries.get(0).getKey()));
        mockMvc.perform(get(ENDPOINT  +"apps/{appId}/keys", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0]", equalTo(entries.get(0).getKey())));
        
        // V2
        mockMvc.perform(get(ENDPOINT_V2  + "apps/{appId}/keys", appId))
	        .andExpect(status().isOk())
	        .andExpect(jsonPath("$.data", hasSize(1)))
	        .andExpect(jsonPath("$.data[0]", equalTo(entries.get(0).getKey())));

    }

    @Test
    void testGetByAppIdAndKeyValue() throws Exception {
        // test we can get a discrete entry by its key name within a given app id

        UUID appId = entries.get(0).getAppId();
        String keyValue = "hello";
        Mockito.when(service.userCanReadFromAppId(Mockito.any(UUID.class), Mockito.anyString(), Mockito.anyBoolean(), Mockito.anyString()))
                .thenReturn(true); // let user have the privs to read
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

        ScratchStorageEntryDto entry = ScratchStorageEntryDto
                .builder()
                .id(UUID.randomUUID())
                .appId(UUID.randomUUID())
                .key("name")
                .value("Chris")
                .build();

        Mockito.when(service.userCanWriteToAppId(Mockito.any(UUID.class), Mockito.anyString(), Mockito.anyBoolean(), Mockito.anyString()))
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

        Mockito.when(service.userCanDeleteKeyForAppId(Mockito.any(UUID.class), Mockito.anyString(), Mockito.anyBoolean(), Mockito.anyString()))
                .thenReturn(true); // let user have the privs to mutate

        Mockito.when(service.deleteAllKeyValuePairsForAppId(entries.get(0).getAppId())).thenReturn(Lists.newArrayList(entries.get(0)));

        mockMvc.perform(delete(ENDPOINT + "{appId}", entries.get(0).getAppId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].appId", equalTo(entries.get(0).getAppId().toString())));
    }

    @Test
    void testDeleteAllByAppIdAndKeyName() throws Exception {
        // test we can delete a specific key value pair for an app given its ID


        Mockito.when(service.userCanDeleteKeyForAppId(Mockito.any(UUID.class), Mockito.anyString(), Mockito.anyBoolean(), Mockito.anyString()))
                .thenReturn(true) // let user have the privs to mutate
                .thenReturn(false);   // deny on second call

        Mockito.when(service.deleteKeyValuePair(entries.get(0).getAppId(), "hello")).thenReturn(entries.get(0));

        mockMvc.perform(delete(ENDPOINT + "{appId}/key/{keyName}", entries.get(0).getAppId(), entries.get(0).getKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId", equalTo(entries.get(0).getAppId().toString())));

        mockMvc.perform(delete(ENDPOINT + "{appId}/key/{keyName}", entries.get(0).getAppId(), entries.get(0).getKey()))
                .andExpect(status().isForbidden());

    }

    //
    // test out the app registration endpoints

    @Test
    void testGetAllRegisteredApps() throws Exception {
        // test we can get all registered apps

        DtoMapper mapper = new DtoMapper();
        List<ScratchStorageAppRegistryDto> dtos = new ArrayList<>();
        for (ScratchStorageAppRegistryDto entry : registeredApps) {
            dtos.add(mapper.map(registeredApps, ScratchStorageAppRegistryDto.class));
        }

        Mockito.when(service.getAllRegisteredScratchApps()).thenReturn(dtos);


        mockMvc.perform(get(SCRATCH_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        
        // V2
        mockMvc.perform(get(SCRATCH_ENDPOINT_V2))
	        .andExpect(status().isOk())
	        .andExpect(jsonPath("$.data", hasSize(2)));
    }
    
    @Test
    void testGetAllAppsContainingUser() throws Exception {
    	DtoMapper mapper = new DtoMapper();
    	
    	List<ScratchStorageAppRegistryDto> dtos = new ArrayList<>();
    	for (ScratchStorageAppRegistryDto entry : registeredApps) {
            dtos.add(mapper.map(entry, ScratchStorageAppRegistryDto.class));
        }
    	
		Mockito.when(service.getAllScratchAppsContainingUser(Mockito.anyString())).thenReturn(dtos);
		
		mockMvc.perform(get(SCRATCH_ENDPOINT + "self"))
	        .andExpect(status().isOk())
	        .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(dtos)));
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

        Mockito.when(service.addNewScratchAppName(Mockito.any(ScratchStorageAppRegistryDto.class)))
                .then(returnsFirstArg());

        ScratchStorageAppRegistryDto newEntry = ScratchStorageAppRegistryDto
                .builder()
                .id(null)
                .appName("TestApp")
                .build();

        mockMvc.perform(post(SCRATCH_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(newEntry)))
                .andExpect(status().isCreated())
                .andExpect(result ->
                        assertEquals(newEntry.getAppName(), OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), ScratchStorageAppRegistryDto.class).getAppName()));
    }

    @Test
    void testEditRegisteredApp() throws Exception {
        // test we can edit an existing app entry

        Mockito.when(service.editExistingScratchAppEntry(Mockito.any(UUID.class), Mockito.any(ScratchStorageAppRegistryDto.class)))
                .then(returnsSecondArg());

        ScratchStorageAppRegistryDto newEntry = ScratchStorageAppRegistryDto
                .builder()
                .id(UUID.randomUUID())
                .appName("TestApp")
                .build();

        mockMvc.perform(put(SCRATCH_ENDPOINT + "{id}", newEntry.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(newEntry)))
                .andExpect(status().isOk())
                .andExpect(result ->
                        assertEquals(newEntry.getAppName(), OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), ScratchStorageAppRegistryDto.class).getAppName()));
    }

    @Test
    void testDeleteRegisteredApp() throws Exception {
        // test we can delete a registered app

        ScratchStorageAppRegistryDto newEntry = ScratchStorageAppRegistryDto
                .builder()
                .id(UUID.randomUUID())
                .appName("TestApp")
                .build();

        Mockito.when(service.deleteScratchStorageApp(Mockito.any(UUID.class))).thenReturn(newEntry);

        mockMvc.perform(delete(SCRATCH_ENDPOINT + "{id}", newEntry.getId()))
                .andExpect(status().isOk())
                .andExpect(result ->
                        assertEquals(newEntry.getAppName(), OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), ScratchStorageAppRegistryDto.class).getAppName()));
    }

    @Test
    void testAddingUserPrivToApp() throws Exception {

        ScratchStorageAppRegistryDto newEntry = ScratchStorageAppRegistryDto
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

        ScratchStorageAppRegistryDto newEntry = ScratchStorageAppRegistryDto
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

        ModelMapper mapper = new ModelMapper();
        Mockito.when(service.getAllScratchUsers())
                .thenReturn(Lists.newArrayList(mapper.map(user1, ScratchStorageUserDto.class)));

        mockMvc.perform(get(SCRATCH_USERS_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        
        // V2
        mockMvc.perform(get(SCRATCH_USERS_ENDPOINT_V2))
	        .andExpect(status().isOk())
	        .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void testAddNewScratchUser() throws Exception {
        // test we can add a new user to the scratch users so they can get a UUID

        ModelMapper mapper = new ModelMapper();
        ScratchStorageUserDto userDto = mapper.map(user1, ScratchStorageUserDto.class);
        Mockito.when(service.addNewScratchUser(Mockito.any(ScratchStorageUserDto.class))).thenReturn(userDto);
        mockMvc.perform(post(SCRATCH_USERS_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(user1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", equalTo(user1.getId().toString())));
    }

    @Test
    void testEditScratchUser() throws Exception {
        // test we can edit a user to the scratch user

        Mockito.when(service.editScratchUser(Mockito.any(UUID.class), Mockito.any(ScratchStorageUserDto.class)))
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

        ModelMapper mapper = new ModelMapper();
        ScratchStorageUserDto userDto = mapper.map(user1, ScratchStorageUserDto.class);
        Mockito.when(service.deleteScratchUser(Mockito.any(UUID.class))).thenReturn(userDto);

        mockMvc.perform(delete(SCRATCH_USERS_ENDPOINT + "{id}", userDto.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(userDto.getId().toString())));
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
                .andExpect(MockMvcResultMatchers.jsonPath("$", hasSize(1)));
        
        // V2
        mockMvc.perform(get(ENDPOINT_V2 + "users/privs"))
	        .andExpect(status().isOk())
	        .andExpect(MockMvcResultMatchers.jsonPath("$.data", hasSize(1)));
    }

    @Test
    void testGetScratchValueAsJson() throws Exception {

        // test a post request to get a scratch key-value as Json (request body is text/plain with the Json Patch Spec)
        UUID appId = entries.get(0).getAppId();
        String keyValue = "hello";
        Mockito.when(service.userCanReadFromAppId(Mockito.any(UUID.class), Mockito.anyString(), Mockito.anyBoolean(), Mockito.anyString()))
                .thenReturn(true); // let user have the privs to read
        Mockito.when(service.getKeyValueEntryByAppId(appId, keyValue)).thenReturn(entries.get(0));
        Mockito.when(service.getKeyValueJson(appId, keyValue, "$.name")).thenReturn("Dude");

        mockMvc.perform(post(ENDPOINT  + "{appId}/{keyValue}/jsonize", appId, keyValue)
                .contentType(MediaType.TEXT_PLAIN)
                .content("$.name"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Dude")));

    }

    @Test
    void testSetScratchValuePartAsJson() throws Exception {

        // test a patch request to change part of a scratch key-value as Json
        ScratchStorageEntryDto entry = ScratchStorageEntryDto
                .builder()
                .id(UUID.randomUUID())
                .appId(UUID.randomUUID())
                .key("name")
                .value("Chris")
                .build();

        Mockito.when(service.userCanWriteToAppId(Mockito.any(UUID.class), Mockito.anyString(), Mockito.anyBoolean(), Mockito.anyString()))
                .thenReturn(true); // let user have the privs to mutate

        Mockito.when(service.setKeyValuePair(entry.getAppId(), entry.getKey(), entry.getValue())).thenReturn(entry);
        Mockito.doNothing().when(service).patchKeyValueJson(entry.getAppId(), entry.getKey(), "Bob", "$.name");

        mockMvc.perform(patch(ENDPOINT + "{appId}/{keyName}/jsonize", entry.getAppId(), entry.getKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(ScratchValuePatchJsonDto.builder().value("Bob").jsonPath("$.name").build())))
                .andExpect(status().isNoContent());

    }
}
