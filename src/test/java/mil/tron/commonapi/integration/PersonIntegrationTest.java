package mil.tron.commonapi.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.entity.Person;
import org.assertj.core.util.Lists;
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
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
public class PersonIntegrationTest {
    private static final String ENDPOINT = "/v1/person/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    private Person person;

    @BeforeEach
    public void insertPerson() throws Exception {
        person = new Person();
        person.setFirstName("John");
        person.setMiddleName("Hero");
        person.setLastName("Public");
        person.setEmail("john@test.com");
        person.setTitle("CAPT");
    }

    @Transactional
    @Rollback
    @Test
    public void testBulkAddPeople() throws Exception {

        Person a2 = new Person();
        a2.setEmail("test1@test.com");
        a2.setTitle("SSGT");

        List<Person> newPeople = Lists.newArrayList(
                person,
                a2
        );

        mockMvc.perform(post(ENDPOINT + "persons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(newPeople)))
                .andExpect(status().isCreated())
                .andExpect(result -> assertEquals(2, OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), Person[].class).length));

        Person a3 = new Person();
        a3.setEmail("test1@test.com");
        a3.setTitle("SSGT");

        // test that we can't add someone with a dup email
        mockMvc.perform(post(ENDPOINT + "persons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(a3))))
                .andExpect(status().isConflict());

        // test pagination
        mockMvc.perform(get(ENDPOINT + "?page=1&limit=4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get(ENDPOINT + "?page=2&limit=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

}
