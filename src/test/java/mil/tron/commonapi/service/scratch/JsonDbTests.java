package mil.tron.commonapi.service.scratch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.scratch.ScratchStorageAppRegistryEntry;
import mil.tron.commonapi.entity.scratch.ScratchStorageAppUserPriv;
import mil.tron.commonapi.entity.scratch.ScratchStorageEntry;
import mil.tron.commonapi.entity.scratch.ScratchStorageUser;
import mil.tron.commonapi.exception.InvalidFieldValueException;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.scratch.InvalidJsonDbSchemaException;
import mil.tron.commonapi.exception.scratch.InvalidJsonPathQueryException;
import mil.tron.commonapi.repository.scratch.ScratchStorageRepository;
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
public class JsonDbTests {

    // tests for JsonDB
    @Mock
    private ScratchStorageRepository repository;

    @InjectMocks
    private JsonDbServiceImpl service;

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

        assertThrows(RecordNotFoundException.class, () -> service.updateElement(appId, "table", "", "{}", "$"));
        assertThrows(InvalidJsonPathQueryException.class, ()-> service.updateElement(appId, "table", "", "{}", "$"));

        // make sure we can update the existing record with given UUID, and we change the name from John to Juan
        //  and verify
        service.updateElement(appId,
                "table",
                "97031086-58a2-4228-8fa6-6d6544c1102d",
                "{ \"id\": \"97031086-58a2-4228-8fa6-6d6544c1102d\", \"name\": \"Juan\", \"email\": \"f@test.com\" }",
                "$[?(@.id == '97031086-58a2-4228-8fa6-6d6544c1102d')]");

        List<Map<String, Object>> result = JsonPath.read(entry.getValue(), "$[?(@.id == '97031086-58a2-4228-8fa6-6d6544c1102d')]");
        assertEquals("Juan", result.get(0).get("name"));

        // make sure we throw error when update-able target does not exist
        assertThrows(RecordNotFoundException.class, () -> service.updateElement(appId,
                "table",
                "97031086-58a2-4228-8fa6-6d6544c1102f",
                "{ \"id\": \"97031086-58a2-4228-8fa6-6d6544c1102f\", \"name\": \"Juan\", \"email\": \"J@test.com\" }",
                "$[?(@.id == '97031086-58a2-4228-8fa6-6d6544c1102e')]"));

        // make sure we throw error when we exclude id field from the update JSON
        assertThrows(InvalidFieldValueException.class, () -> service.updateElement(appId,
                "table",
                "97031086-58a2-4228-8fa6-6d6544c1102f",
                "{ \"name\": \"Juan\", \"email\": \"J@test.com\" }",
                "$[?(@.id == '97031086-58a2-4228-8fa6-6d6544c1102e')]"));

        // make sure we throw error when provided entity ID not equal to the ID field in the update JSON
        assertThrows(InvalidFieldValueException.class, () -> service.updateElement(appId,
                "table",
                "97031086-58a2-4228-8fa6-6d6544c1102f",
                "{ \"id\": \"97031086-58a2-4228-8fa6-6d6544c1102e\", \"name\": \"Juan\", \"email\": \"J@test.com\" }",
                "$[?(@.id == '97031086-58a2-4228-8fa6-6d6544c1102e')]"));

        // make sure we throw error when provided update is malformed
        assertThrows(InvalidFieldValueException.class, () -> service.updateElement(appId,
                "table",
                "97031086-58a2-4228-8fa6-6d6544c1102f",
                " \"id\": \"97031086-58a2-4228-8fa6-6d6544c1102e\", \"name\": \"Juan\", \"email\": \"J@test.com\" }",
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

        assertEquals("[]", service.queryJson(appId, "table","$[?(@.email =~ /.*test.mil/i)]").toString());

    }

    @Test
    void testInvalidJsonDbSchemaDetected() {
        UUID appId = UUID.randomUUID();

        String jsonValue = "[ " +
                "{ \"id\": \"97031086-58a2-4228-8fa6-6d6544c1102d\", \"age\": 40, \"name\": \"Frank\", \"email\": \"f@test.com\" }, " +
                "{ \"id\": \"97031086-58a2-4228-8fa6-6d6544c1102e\", \"age\": 41, \"name\": \"Bill\", \"email\": \"b@test.com\" } " +
                "]";
        String schema = "{ \"age\": \"number\", \"name\": \"string\", \"email\": \"email!*\" }";
        String schema1 = "{ \"id\": \"string\", \"age\": \"number\", \"name\": \"string\", \"email\": \"email!*\" }";
        String schema2 = "{ \"id\": \"string\", \"age\": [\"number\"], \"name\": \"string\", \"email\": \"email!*\" }";

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

        ScratchStorageEntry schemaEntry1 = ScratchStorageEntry.builder()
                .id(UUID.randomUUID())
                .key("table_schema")
                .value(schema1)
                .build();

        ScratchStorageEntry schemaEntry2 = ScratchStorageEntry.builder()
                .id(UUID.randomUUID())
                .key("table_schema")
                .value(schema2)
                .build();

        Mockito.when(repository.findByAppIdAndKey(appId, "table"))
                .thenReturn(Optional.ofNullable(entry));

        Mockito.when(repository.findByAppIdAndKey(appId, "table_schema"))
                .thenReturn(Optional.ofNullable(schemaEntry))
                .thenReturn(Optional.ofNullable(schemaEntry1))
                .thenReturn(Optional.ofNullable(schemaEntry2));

        assertThrows(InvalidJsonDbSchemaException.class, () -> service.addElement(appId,
                "table",
                "{ \"id\": \"97031086-58a2-4228-8fa6-6d6544c1102e\", \"age\": 41, \"name\": \"Bill\", \"email\": \"b@test.com\" } "));

        assertThrows(InvalidJsonDbSchemaException.class, () -> service.addElement(appId,
                "table",
                "{ \"id\": \"97031086-58a2-4228-8fa6-6d6544c1102e\", \"age\": 41, \"name\": \"Bill\", \"email\": \"b@test.com\" }"));

        assertThrows(InvalidJsonDbSchemaException.class, () -> service.addElement(appId,
                "table",
                "{ \"id\": \"97031086-58a2-4228-8fa6-6d6544c1102e\", \"age\": 41, \"name\": \"Bill\", \"email\": \"b@test.com\" } "));
    }

    @Test
    void testJsonDbForeignJoins() throws JsonProcessingException {

        // a simple two table one-to-one linkage

        UUID appId = UUID.randomUUID();

        String users = "[ " +
                "{ \"id\": \"97031086-58a2-4228-8fa6-6d6544c1102d\", \"name\": \"Frank\", \"roleId\": \"97031086-58a2-4228-8fa6-6d6544c1103d\" }, " +
                "{ \"id\": \"97031086-58a2-4228-8fa6-6d6544c1102e\", \"name\": \"Bill\", \"roleId\": \"97031086-58a2-4228-8fa6-6d6544c1103e\"} " +
                "]";
        String usersSchema = "{ \"id\": \"uuid\", \"name\": \"string\", \"roleId\": \"foreign-roles\" }";

        String roles = "[ " +
                "{ \"id\": \"97031086-58a2-4228-8fa6-6d6544c1103d\", \"name\" : \"ADMIN\" }, " +
                "{ \"id\": \"97031086-58a2-4228-8fa6-6d6544c1103e\", \"name\" : \"USER\" }, " +
                "{ \"id\": \"97031086-58a2-4228-8fa6-6d6544c1103f\", \"name\" : \"MAINT\" } " +
                "]";
        String rolesSchema = "{ \"id\": \"uuid\", \"name\": \"string*\" }";

        Map<String, ScratchStorageEntry> db = new HashMap<>();

        db.put("users", ScratchStorageEntry.builder()
                .id(UUID.randomUUID())
                .key("users")
                .value(users)
                .build());

        db.put("users_schema", ScratchStorageEntry.builder()
                .id(UUID.randomUUID())
                .key("users_schema")
                .value(usersSchema)
                .build());

        db.put("roles", ScratchStorageEntry.builder()
                .id(UUID.randomUUID())
                .key("roles")
                .value(roles)
                .build());

        db.put("roles_schema", ScratchStorageEntry.builder()
                .id(UUID.randomUUID())
                .key("roles_schema")
                .value(rolesSchema)
                .build());

        Mockito.when(repository.existsByAppIdAndKey(Mockito.any(), Mockito.any())).thenReturn(true);
        Mockito.when(repository.findByAppIdAndKey(Mockito.any(), Mockito.any()))
                .thenAnswer(invocationOnMock -> Optional.of(db.getOrDefault(invocationOnMock.getArgument(1).toString(), null)));


        Object result = service.queryJson(appId, "users", "$[?(@.name == 'Frank')]");
        assertNotNull(result);
    }
}
