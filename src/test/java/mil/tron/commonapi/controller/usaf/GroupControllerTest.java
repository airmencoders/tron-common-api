package mil.tron.commonapi.controller.usaf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.dto.OrganizationDto;
import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.orgtypes.Unit;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.service.OrganizationService;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GroupController.class)
public class GroupControllerTest {
    private static final String ENDPOINT = "/v1/group/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrganizationService organizationService;

    private Person testPerson;
    private Person testLeaderPerson;
    private Organization testOrg;
    private OrganizationDto testOrgDto;

    @BeforeEach
    public void beforeEachTest() throws JsonProcessingException {
        testPerson = new Person();
        testPerson.setFirstName("Test");
        testPerson.setLastName("Person");
        testPerson.setMiddleName("MVC");
        testPerson.setTitle("Person Title");
        testPerson.setEmail("test.person@mvc.com");

        testLeaderPerson = new Person();
        testLeaderPerson.setFirstName("Test");
        testLeaderPerson.setLastName("Person");
        testLeaderPerson.setMiddleName("Leader");
        testLeaderPerson.setTitle("Leader Person");
        testLeaderPerson.setEmail("test.leader@person.com");

        testOrg = new Organization();
        testOrg.setName("Test Org");
        testOrg.setLeader(testLeaderPerson);
        testOrg.addMember(testPerson);

        testOrgDto = OrganizationDto.builder()
                .id(testOrg.getId())
                .leader(testOrg.getLeader().getId())
                .name(testOrg.getName())
                .members(testOrg.getMembers().stream().map(Person::getId).collect(Collectors.toSet()))
                .orgType(Unit.GROUP)
                .build();

    }

    @Nested
    class TestGet {

        @Test
        void testGetAll() throws Exception {
            List<OrganizationDto> orgs = Lists.newArrayList(testOrgDto);

            Mockito.when(organizationService.getOrganizationsByType(Unit.GROUP)).thenReturn(orgs);

            mockMvc.perform(get(ENDPOINT))
                    .andExpect(status().isOk())
                    .andExpect(result -> assertThat(OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), OrganizationDto[].class)).hasSize(1));
        }

        @Test
        void testGetById() throws Exception {
            Mockito.when(organizationService.getOrganization(Mockito.any(UUID.class))).thenReturn(testOrgDto);

            mockMvc.perform(get(ENDPOINT + "{id}", testOrgDto.getId()))
                    .andExpect(status().isOk())
                    .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(testOrgDto)));
        }

        @Test
        void testGetByIdNotFound() throws Exception {
            Mockito.when(organizationService.getOrganization(Mockito.any(UUID.class))).thenThrow(RecordNotFoundException.class);

            mockMvc.perform(get(ENDPOINT + "{id}", testOrgDto.getId()))
                    .andExpect(status().isNotFound());
        }

        @Test
        void testGetByIdThatsNotAGroup() throws Exception {
            testOrgDto.setOrgType(Unit.SQUADRON);
            Mockito.when(organizationService.getOrganization(Mockito.any(UUID.class))).thenReturn(testOrgDto);

            mockMvc.perform(get(ENDPOINT + "{id}", testOrgDto.getId()))
                    .andExpect(status().isNotFound());
        }

        @Test
        void testGetByIdBadPathVariable() throws Exception {
            // Send an invalid UUID as ID path variable
            mockMvc.perform(get(ENDPOINT + "{id}", "asdf1234"))
                    .andExpect(status().isBadRequest())
                    .andExpect(result -> assertTrue(result.getResolvedException() instanceof MethodArgumentTypeMismatchException));
        }
    }

    @Nested
    class TestPost {
        @Test
        void testPostValidJsonBody() throws Exception {
            Mockito.when(organizationService.createOrganization(Mockito.any(OrganizationDto.class))).thenReturn(testOrgDto);

            mockMvc.perform(post(ENDPOINT)
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(testOrgDto)))
                    .andExpect(status().isCreated())
                    .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(testOrgDto)));
        }

        @Test
        void testPostInvalidJsonBody() throws Exception {
            // Send empty string as bad json data
            mockMvc.perform(post(ENDPOINT).accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).content(""))
                    .andExpect(status().isBadRequest())
                    .andExpect(result -> assertTrue(result.getResolvedException() instanceof HttpMessageNotReadableException));
        }

        @Test
        void testPostOrganizationWithIdAlreadyExists() throws Exception {
            Mockito.when(organizationService.createOrganization(Mockito.any(OrganizationDto.class))).thenThrow(ResourceAlreadyExistsException.class);

            mockMvc.perform(post(ENDPOINT)
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(testOrgDto)))
                    .andExpect(status().isConflict());
        }

        @Test
        void testBulkCreate() throws Exception {
            List<OrganizationDto> newOrgs = Lists.newArrayList(
                    (OrganizationDto.builder().id(UUID.randomUUID()).orgType(Unit.GROUP).build()),
                    (OrganizationDto.builder().id(UUID.randomUUID()).orgType(Unit.GROUP).build()),
                    (OrganizationDto.builder().id(UUID.randomUUID()).orgType(Unit.GROUP).build()),
                    (OrganizationDto.builder().id(UUID.randomUUID()).orgType(Unit.GROUP).build())
            );

            Mockito.when(organizationService.bulkAddOrgs(Mockito.anyList())).then(returnsFirstArg());

            mockMvc.perform(post(ENDPOINT + "/groups")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(newOrgs)))
                    .andExpect(status().isCreated())
                    .andExpect(result -> assertEquals(OBJECT_MAPPER.writeValueAsString(newOrgs), result.getResponse().getContentAsString()));

        }

        @Test
        void testBulkCreateWithOneNonGroupUnit() throws Exception {
            List<OrganizationDto> newOrgs = Lists.newArrayList(
                    (OrganizationDto.builder().id(UUID.randomUUID()).orgType(Unit.GROUP).build()),
                    (OrganizationDto.builder().id(UUID.randomUUID()).orgType(Unit.GROUP).build()),
                    (OrganizationDto.builder().id(UUID.randomUUID()).orgType(Unit.SQUADRON).build()),
                    (OrganizationDto.builder().id(UUID.randomUUID()).orgType(Unit.GROUP).build())
            );

            mockMvc.perform(post(ENDPOINT + "/groups")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(newOrgs)))
                    .andExpect(status().isBadRequest());

        }
    }

    @Nested
    class TestPut {
        @Test
        void testPutValidJsonBody() throws Exception {
            Mockito.when(organizationService.updateOrganization(Mockito.any(UUID.class), Mockito.any(OrganizationDto.class))).thenReturn(testOrgDto);

            mockMvc.perform(put(ENDPOINT + "{id}", testOrgDto.getId())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(testOrgDto)))
                    .andExpect(status().isOk())
                    .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(testOrgDto)));
        }

        @Test
        void testPutInvalidJsonBody() throws Exception {
            // Send empty string as bad json data
            mockMvc.perform(put(ENDPOINT + "{id}", testOrgDto.getId())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(""))
                    .andExpect(status().isBadRequest())
                    .andExpect(result -> assertTrue(result.getResolvedException() instanceof HttpMessageNotReadableException));
        }

        @Test
        void testPutInvalidBadPathVariable() throws Exception {
            // Send an invalid UUID as ID path variable
            mockMvc.perform(put(ENDPOINT + "{id}", "asdf1234"))
                    .andExpect(status().isBadRequest())
                    .andExpect(result -> assertTrue(result.getResolvedException() instanceof MethodArgumentTypeMismatchException));
        }

        @Test
        void testPutResourceDoesNotExist() throws Exception {
            Mockito.when(organizationService.updateOrganization(Mockito.any(UUID.class), Mockito.any(OrganizationDto.class))).thenReturn(null);

            mockMvc.perform(put(ENDPOINT + "{id}", testOrg.getId())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(testOrgDto)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void testUpdateUnitThatsNotAGroup() throws Exception {
            testOrgDto.setOrgType(Unit.SQUADRON);
            Mockito.when(organizationService.updateOrganization(Mockito.any(UUID.class), Mockito.any(OrganizationDto.class))).thenReturn(testOrgDto);

            mockMvc.perform(put(ENDPOINT + "{id}", testOrg.getId())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(testOrgDto)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class TestDelete {
        @Test
        void testDelete() throws Exception {
            Mockito.when(organizationService.getOrganization(Mockito.any(UUID.class))).thenReturn(testOrgDto);

            mockMvc.perform(delete(ENDPOINT + "{id}", testOrgDto.getId()))
                    .andExpect(status().isNoContent());
        }

        @Test
        void testDeleteBadPathVariable() throws Exception {
            mockMvc.perform(delete(ENDPOINT + "{id}", "asdf1234"))
                    .andExpect(status().isBadRequest())
                    .andExpect(result -> assertTrue(result.getResolvedException() instanceof MethodArgumentTypeMismatchException));
        }

        @Test
        void testUnitThatsNotAGroup() throws Exception {
            testOrgDto.setOrgType(Unit.SQUADRON);
            Mockito.when(organizationService.getOrganization(Mockito.any(UUID.class))).thenReturn(testOrgDto);

            mockMvc.perform(delete(ENDPOINT + "{id}", testOrgDto.getId()))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class TestPatch {
        @Test
        void testChangeName() throws Exception {
            Map<String, String> attribs = new HashMap<>();
            attribs.put("name", "test org");
            OrganizationDto newOrg = new OrganizationDto();
            newOrg.setId(testOrgDto.getId());
            newOrg.setName("test org");

            Mockito.when(organizationService.getOrganization(Mockito.any(UUID.class))).thenReturn(testOrgDto);
            Mockito.when(organizationService.modifyAttributes(Mockito.any(UUID.class), Mockito.any(Map.class))).thenReturn(newOrg);
            MvcResult result = mockMvc.perform(patch(ENDPOINT + "{id}", testOrgDto.getId())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(attribs)))
                    .andExpect(status().isOk())
                    .andReturn();

            assertEquals("test org", OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), OrganizationDto.class).getName());
        }

        @Test
        void testChangeNameNonGroupUnit() throws Exception {
            Map<String, String> attribs = new HashMap<>();
            attribs.put("name", "test org");

            testOrgDto.setOrgType(Unit.SQUADRON);
            Mockito.when(organizationService.getOrganization(Mockito.any(UUID.class))).thenReturn(testOrgDto);

            mockMvc.perform(patch(ENDPOINT + "{id}", testOrgDto.getId())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(attribs)))
                    .andExpect(status().isBadRequest());

        }

        @Test
        void testAddRemoveMember() throws Exception {
            Person p = new Person();

            OrganizationDto newOrg = new OrganizationDto();
            newOrg.setId(testOrgDto.getId());
            newOrg.setName("test org");
            newOrg.getMembers().add(p.getId());

            Mockito.when(organizationService.getOrganization(Mockito.any(UUID.class))).thenReturn(testOrgDto);
            Mockito.when(organizationService.addOrganizationMember(Mockito.any(UUID.class), Mockito.any(List.class))).thenReturn(newOrg);
            MvcResult result = mockMvc.perform(patch(ENDPOINT + "{id}/members", testOrgDto.getId())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(p.getId()))))
                    .andExpect(status().isOk())
                    .andReturn();

            // test it "added" to org
            assertEquals(1, OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), OrganizationDto.class).getMembers().size());

            newOrg.getMembers().remove(p.getId());
            Mockito.when(organizationService.removeOrganizationMember(Mockito.any(UUID.class), Mockito.any(List.class))).thenReturn(newOrg);
            MvcResult result2 = mockMvc.perform(delete(ENDPOINT + "{id}/members", testOrgDto.getId())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(p.getId()))))
                    .andExpect(status().isOk())
                    .andReturn();

            // test it "removed" from org
            assertEquals(0, OBJECT_MAPPER.readValue(result2.getResponse().getContentAsString(), OrganizationDto.class).getMembers().size());
        }

        @Test
        void testAddRemoveMemberOnANonGroupUnit() throws Exception {
            Person p = new Person();
            testOrgDto.setOrgType(Unit.SQUADRON);
            Mockito.when(organizationService.getOrganization(Mockito.any(UUID.class))).thenReturn(testOrgDto);
            mockMvc.perform(patch(ENDPOINT + "{id}/members", testOrgDto.getId())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(p.getId()))))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(delete(ENDPOINT + "{id}/members", testOrgDto.getId())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(p.getId()))))
                    .andExpect(status().isBadRequest());

        }
    }

}
