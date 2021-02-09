package mil.tron.commonapi.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.branches.Branch;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
public class PersonIntegrationTest {
    private static final String ENDPOINT = "/v1/person/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    private PersonDto person;

    @BeforeEach
    public void insertPerson() {
        person = new PersonDto();
        person.setFirstName("John");
        person.setMiddleName("Hero");
        person.setLastName("Public");
        person.setEmail("john@test.com");
        person.setTitle("CAPT");
        person.setRank("Capt");
        person.setBranch(Branch.USAF);
    }

    @Transactional
    @Rollback
    @Test
    public void testBulkAddPeople() throws Exception {

        PersonDto a2 = new PersonDto();
        a2.setEmail("test1@test.com");
        a2.setTitle("SSGT");
        a2.setRank("SSgt");
        a2.setBranch(Branch.USAF);

        List<PersonDto> newPeople = Lists.newArrayList(
                person,
                a2
        );

        mockMvc.perform(post(ENDPOINT + "persons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(newPeople)))
                .andExpect(status().isCreated())
                .andExpect(result -> assertEquals(2, OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), PersonDto[].class).length));

        PersonDto a3 = new PersonDto();
        a3.setEmail("test1@test.com");
        a3.setTitle("SSGT");
        a3.setRank("SSgt");
        a3.setBranch(Branch.USAF);

        // test that we can't add someone with a dup email
        mockMvc.perform(post(ENDPOINT + "persons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(a3))))
                .andExpect(status().isConflict());
    }

}
