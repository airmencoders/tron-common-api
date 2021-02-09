package mil.tron.commonapi.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.dto.OrganizationDto;
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
public class OrganizationIntegrationTest {

    private static final String ENDPOINT = "/v1/organization/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    private OrganizationDto organization;

    @BeforeEach
    public void insertSquadron() throws Exception {
        organization = new OrganizationDto();
        organization.setName("TEST ORG");
        organization.setMembers(null);
    }

    @Test
    @Transactional
    @Rollback
    void testBulkAddOrganizations() throws Exception {

        OrganizationDto s2 = new OrganizationDto();
        s2.setName("TEST2");
        s2.setMembers(null);

        OrganizationDto s3 = new OrganizationDto();
        s3.setName("TEST3");
        s3.setMembers(null);

        List<OrganizationDto> newOrganizations = Lists.newArrayList(
                organization,
                s2,
                s3
        );

        // test go path for controller to db for adding build organizations, add 3 get back 3
        mockMvc.perform(post(ENDPOINT + "organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(newOrganizations)))
                .andExpect(status().isCreated())
                .andExpect(result -> assertEquals(3, OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), OrganizationDto[].class).length));

        // now try to add again one that already has an existing name
        OrganizationDto s4 = new OrganizationDto();
        s4.setName(organization.getName());
        mockMvc.perform(post(ENDPOINT + "organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(s4))))
                .andExpect(status().isConflict());

        // test pagination
        mockMvc.perform(get(ENDPOINT + "?page=1&limit="))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        mockMvc.perform(get(ENDPOINT + "?page=2&limit=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

    }
}
