package mil.tron.commonapi.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import mil.tron.commonapi.dto.OrganizationDto;
import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.repository.OrganizationRepository;
import mil.tron.commonapi.repository.PersonRepository;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.transaction.Transactional;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that launches the FULL server and Spring error handlers so we can
 * test the custom error responses (MockMvc uses its own error handlers).  We use a native Java
 * HttpClient to do the HTTP request.
 */

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = { "efa-enabled=false", "security.enabled=false"})
@ActiveProfiles(value = { "development", "test" })  // enable at least dev so we get tracing enabled for full integration
public class ErrorValidationIntegrationTest {

    @LocalServerPort
    int randomServerPort;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private PersonRepository personRepository;

    @Test
    void testValidationErrorField() throws Exception {

        PersonDto person = new PersonDto();
        person.setFirstName("John");
        person.setMiddleName("Hero");
        person.setLastName("Public");
        person.setEmail("jhp@test.com");
        person.setDodid("");
        person.setTitle("CAPT");
        person.setRank("Capt");
        person.setBranch(Branch.USAF);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(String.format("http://localhost:%d/api/v2/person", randomServerPort)))
                .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(person)))
                .header("content-type", "application/json")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
        List<String> errorMessages = JsonPath.read(response.body(), "$.errors[*].defaultMessage");
        assertIterableEquals(List.of("An acceptable DODID must be 5-10 digits or a null value"), errorMessages);

        person.setDodid(null);
        request = HttpRequest.newBuilder()
                .uri(new URI(String.format("http://localhost:%d/api/v2/person", randomServerPort)))
                .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(person)))
                .header("content-type", "application/json")
                .build();

        response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(201, response.statusCode());
        UUID id = UUID.fromString(JsonPath.read(response.body(), "$.id"));

        // do an invalid json patch so we can test for a generalized response error message
        request = HttpRequest.newBuilder()
                .uri(new URI(String.format("http://localhost:%d/api/v2/person/%s", randomServerPort, id)))
                .method("PATCH", HttpRequest.BodyPublishers.ofString("{}"))
                .header("content-type", "application/json-patch+json")
                .build();

        response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response.statusCode());
        assertTrue(JsonPath.read(response.body(), "$.reason").toString().contains("check format of the request"));
    }


    @AfterEach
    public void cleanup() {

        // we have to manually cleanup since running the full server container
        //  the rollback stuff doesn't work per Spring Docs.

        personRepository.deleteAllInBatch();
        organizationRepository.deleteAllInBatch();
    }

    @Test
    void testCantPatchPersonOrgFields() throws Exception {

        // test that person's organizationMemberships and organizationLeaderships fields
        //  cannot be PUT, POST, or JSON PATCH'd thru the Person API
        PersonDto person = PersonDto.builder()
                .id(UUID.randomUUID())
                .firstName("test")
                .lastName("member")
                .email("dude@member.com")
                .rank("CIV")
                .branch(Branch.USAF)
                .organizationMemberships(Set.of(UUID.randomUUID()))
                .dodid("12345")
                .build();

        // test POST
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(String.format("http://localhost:%d/api/v2/person", randomServerPort)))
                .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(person)))
                .header("content-type", "application/json")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(HttpStatus.CREATED.value(), response.statusCode());

        // verify that field was ignored
        Set<UUID> orgMemberships = JsonPath.using(Configuration.builder().options(Option.SUPPRESS_EXCEPTIONS).build())
                .parse(response.body())
                .read("$.organizationMemberships");

        assertTrue(orgMemberships == null || orgMemberships.isEmpty());

        // test PUT
        person.setOrganizationMemberships(Set.of(UUID.randomUUID()));
        request = HttpRequest.newBuilder()
                .uri(new URI(String.format("http://localhost:%d/api/v2/person/%s", randomServerPort, person.getId())))
                .PUT(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(person)))
                .header("content-type", "application/json")
                .build();

        response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(HttpStatus.OK.value(), response.statusCode());

        Set<UUID> orgLeaderships = JsonPath.using(Configuration.builder().options(Option.SUPPRESS_EXCEPTIONS).build())
                .parse(response.body())
                .read("$.organizationLeaderships");
        assertTrue(orgLeaderships == null || orgLeaderships.isEmpty());

        // add a real org now with the person we just created in it
        OrganizationDto org = OrganizationDto
                .builder()
                .id(UUID.randomUUID())
                .name("ORG")
                .members(Lists.newArrayList(person.getId()))
                .build();
        request = HttpRequest.newBuilder()
                .uri(new URI(String.format("http://localhost:%d/api/v2/organization", randomServerPort)))
                .method("POST", HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(org)))
                .header("content-type", "application/json")
                .build();
        response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(HttpStatus.CREATED.value(), response.statusCode());

        List<UUID> members = JsonPath.parse(response.body()).read("$.members");
        assertFalse(members.isEmpty());

        // check JSON PATCH fails--
        // test you can't patch the organizationMemberships field since its annotated as @NonPatchableField
        String patchSpec = "[ {\"op\" : \"add\", \"path\": \"/organizationMemberships/-\", \"value\": \"" + UUID.randomUUID() + "\"}]";
        request = HttpRequest.newBuilder()
                .uri(new URI(String.format("http://localhost:%d/api/v2/person/%s", randomServerPort, person.getId())))
                .method("PATCH", HttpRequest.BodyPublishers.ofString(patchSpec))
                .header("content-type", "application/json-patch+json")
                .build();

        response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.statusCode());
        assertTrue(response.body().contains("Cannot JSON Patch the field"));

        // test you can't patch the organizationLeaderships field since its annotated as @NonPatchableField
        patchSpec = "[ {\"op\" : \"add\", \"path\": \"/organizationLeaderships/-\", \"value\": \"" + UUID.randomUUID() + "\"}]";
        request = HttpRequest.newBuilder()
                .uri(new URI(String.format("http://localhost:%d/api/v2/person/%s", randomServerPort, person.getId())))
                .method("PATCH", HttpRequest.BodyPublishers.ofString(patchSpec))
                .header("content-type", "application/json-patch+json")
                .build();

        response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.statusCode());
        assertTrue(response.body().contains("Cannot JSON Patch the field"));
    }

}
