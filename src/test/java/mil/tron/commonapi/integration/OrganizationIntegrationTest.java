package mil.tron.commonapi.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.dto.OrganizationDto;
import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.service.OrganizationService;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Autowired
    private OrganizationService organizationService;

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

    }

    @Test
    @Rollback
    @Transactional
    void testAncestryLogic() throws Exception {

        OrganizationDto greatGrandParent = new OrganizationDto();
        greatGrandParent.setName("Great Grandpa");
        OrganizationDto grandParent = new OrganizationDto();
        grandParent.setName("Grandpa");
        OrganizationDto parent = new OrganizationDto();
        parent.setName("Father");
        OrganizationDto theOrg = new OrganizationDto();
        theOrg.setName("Son");
        OrganizationDto legitSubOrg = new OrganizationDto();
        legitSubOrg.setName("Grandson");

        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(greatGrandParent)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(grandParent)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(parent)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(theOrg)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(legitSubOrg)))
                .andExpect(status().isCreated());

        Map<String, UUID> attribs = new HashMap<>();
        attribs.put("parentOrganization", parent.getId());
        mockMvc.perform(patch(ENDPOINT + "{id}", theOrg.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(attribs)))
                .andExpect(status().isOk());

        Map<String, UUID> attribs2 = new HashMap<>();
        attribs2.put("parentOrganization", grandParent.getId());
        mockMvc.perform(patch(ENDPOINT + "{id}", parent.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(attribs2)))
                .andExpect(status().isOk());

        Map<String, UUID> attribs3 = new HashMap<>();
        attribs3.put("parentOrganization", greatGrandParent.getId());
        mockMvc.perform(patch(ENDPOINT + "{id}", grandParent.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(attribs3)))
                .andExpect(status().isOk());

        // now try to set theOrgs subordinate organization to be the grandparent
        List<UUID> subOrgs = Lists.newArrayList(grandParent.getId());
        mockMvc.perform(patch(ENDPOINT + "{id}/subordinates", theOrg.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(subOrgs)))
                .andExpect(status().isBadRequest());

        // now try to set theOrgs subordinate organization to be the son, be OK
        subOrgs = Lists.newArrayList(legitSubOrg.getId());
        mockMvc.perform(patch(ENDPOINT + "{id}/subordinates", theOrg.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(subOrgs)))
                .andExpect(status().isOk());

    }
}
