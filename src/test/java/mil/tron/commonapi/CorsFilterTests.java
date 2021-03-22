package mil.tron.commonapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.service.PersonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
@AutoConfigureMockMvc
public class CorsFilterTests {
    private static final String ENDPOINT = "/v1/person/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PersonService personService;

    private PersonDto testPerson;
    private String testPersonJson;

    @BeforeEach
    void beforeEachTest() throws JsonProcessingException {
        testPerson = new PersonDto();
        testPerson.setFirstName("Test");
        testPerson.setLastName("Person");
        testPerson.setMiddleName("MVC");
        testPerson.setTitle("Person Title");
        testPerson.setEmail("test.person@mvc.com");

        testPersonJson = OBJECT_MAPPER.writeValueAsString(testPerson);
    }

    @Test
    void testCORS() throws Exception {
        // Expect POST fail due to Origin not on allowed list
        mockMvc.perform(options(ENDPOINT)
                .header("Access-Control-Request-Method", "POST")
                .header("Origin", "http://localhost:8081"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(ENDPOINT)
                .header("Origin", "http://localhost:8080"))
                .andExpect(status().isOk());

        mockMvc.perform(
                options(ENDPOINT)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Origin", "http://localhost:8080"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Access-Control-Allow-Methods")).contains("POST"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:8080"));

        // Expect POST fail due to Origin not on allowed list
        Mockito.when(personService.createPerson(Mockito.any(PersonDto.class))).thenReturn(testPerson);
        mockMvc.perform(post(ENDPOINT)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(testPersonJson)
                .header("Origin", "http://localhost:8081"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testScratchSpaceCors() throws Exception {

        // test that the scratch area is accessible from any dso.mil subdomain

        final String SCRATCH_ENDPOINT = "/v1/scratch";
        mockMvc.perform(get(SCRATCH_ENDPOINT)
                .header("Origin", "http://localhost:9000"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(SCRATCH_ENDPOINT)
                .header("Origin", "http://localhost:8080"))
                .andExpect(status().isOk());

        mockMvc.perform(get(SCRATCH_ENDPOINT)
                .header("Origin", "https://someapp.staging.dso.mil"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo("https://someapp.staging.dso.mil"))
                .andExpect(status().isOk());

        mockMvc.perform(get(ENDPOINT)
                .header("Origin", "https://someapp.staging.dso.mil"))
                .andExpect(status().isForbidden());
    }
}
