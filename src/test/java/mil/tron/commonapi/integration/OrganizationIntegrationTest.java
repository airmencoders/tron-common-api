package mil.tron.commonapi.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.entity.Organization;

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
public class OrganizationIntegrationTest {

    private static final String ENDPOINT = "/v1/organization/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    private Organization organization;

    @BeforeEach
    public void insertSquadron() throws Exception {
        organization = new Organization();
        organization.setName("TEST ORG");
    }

    @Test
    @Transactional
    @Rollback
    void testBulkAddOrganizations() throws Exception {

        Organization s2 = new Organization();
        s2.setName("TEST2");

        Organization s3 = new Organization();
        s3.setName("TEST3");

        List<Organization> newOrganizations = Lists.newArrayList(
                organization,
                s2,
                s3
        );

        // test go path for controller to db for adding build organizations, add 3 get back 3
        mockMvc.perform(post(ENDPOINT + "organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(newOrganizations)))
                .andExpect(status().isCreated())
                .andExpect(result -> assertEquals(3, OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), Organization[].class).length));

        // now try to add again one that already has an existing name
        Organization s4 = new Organization();
        s4.setName(organization.getName());
        mockMvc.perform(post(ENDPOINT + "organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(s4))))
                .andExpect(status().isConflict());

    }
}
