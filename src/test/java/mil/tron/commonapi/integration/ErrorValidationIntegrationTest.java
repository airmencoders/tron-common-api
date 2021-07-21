package mil.tron.commonapi.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.entity.branches.Branch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.transaction.Transactional;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @Transactional
    @Rollback
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

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(String.format("http://localhost:%d/api/v2/person", randomServerPort)))
                .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(person)))
                .header("content-type", "application/json")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
        assertEquals("An acceptable DODID must be 5-10 digits or a null value", JsonPath.read(response.body(), "$.reason"));
    }
}
