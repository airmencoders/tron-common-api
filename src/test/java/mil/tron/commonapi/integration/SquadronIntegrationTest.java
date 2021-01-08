package mil.tron.commonapi.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.dto.SquadronDto;
import mil.tron.commonapi.entity.Airman;
import org.assertj.core.util.Lists;
import org.assertj.core.util.Maps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
public class SquadronIntegrationTest {
    private static final String ENDPOINT = "/v1/squadron/";
    private static final String AIRMAN_ENDPOINT = "/v1/airman/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    private SquadronDto squadron;

    @BeforeEach
    public void insertSquadron() throws Exception {
        squadron = new SquadronDto();
        squadron.setName("TEST ORG");
        squadron.setMajorCommand("ACC");
        squadron.setBaseName("Travis AFB");
    }

    @Transactional
    @Rollback
    @Test
    public void testAddNewSquadron() throws Exception {
        MvcResult response = mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON).content(OBJECT_MAPPER.writeValueAsString(squadron)))
                .andExpect(status().is(HttpStatus.CREATED.value()))
                .andReturn();
        assertEquals(OBJECT_MAPPER.writeValueAsString(squadron), response.getResponse().getContentAsString());
    }

    @Transactional
    @Rollback
    @Test
    public void testAddNewSquadronWithNullId() throws Exception {

        // simulate request with id as null...
        String strSquadron = "{\"id\":null,\"name\":\"TEST ORG\",\"members\":[],\"leader\":null,\"parentOrganization\":null,\"operationsDirector\":null,\"chief\":null,\"baseName\":\"Travis AFB\",\"majorCommand\":\"ACC\"}";

        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON).content(strSquadron))
                .andExpect(status().is(HttpStatus.CREATED.value()));

    }

    @Transactional
    @Rollback
    @Test
    public void testAddNewSquadronOverwriteExistingFails() throws Exception {

        MvcResult response = mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON).content(OBJECT_MAPPER.writeValueAsString(squadron)))
                .andExpect(status().is(HttpStatus.CREATED.value()))
                .andReturn();

        SquadronDto newSquadron = OBJECT_MAPPER.readValue(response.getResponse().getContentAsString(), SquadronDto.class);

        // this POST will fail since it'll detect UUID in db already exists
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON).content(OBJECT_MAPPER.writeValueAsString(newSquadron)))
                .andExpect(status().is(HttpStatus.CONFLICT.value()));

    }

    @Transactional
    @Rollback
    @Test
    public void testGetSquadron() throws Exception {

        MvcResult response = mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON).content(OBJECT_MAPPER.writeValueAsString(squadron)))
                .andExpect(status().is(HttpStatus.CREATED.value()))
                .andReturn();

        SquadronDto newUnit = OBJECT_MAPPER.readValue(response.getResponse().getContentAsString(), SquadronDto.class);
        UUID id = newUnit.getId();

        MvcResult response2 = mockMvc.perform(get(ENDPOINT + id.toString()))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(OBJECT_MAPPER.writeValueAsString(newUnit), response2.getResponse().getContentAsString());
    }

    @Transactional
    @Rollback
    @Test
    public void testGetBogusSquadronFails() throws Exception {

        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON).content(OBJECT_MAPPER.writeValueAsString(squadron)))
                .andExpect(status().is(HttpStatus.CREATED.value()));

        UUID id = UUID.randomUUID();

        mockMvc.perform(get(ENDPOINT + id.toString()))
                .andExpect(status().is(HttpStatus.NOT_FOUND.value()));
    }

    @Transactional
    @Rollback
    @Test
    public void testUpdateSquadron() throws Exception {

        MvcResult response = mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(squadron)))
                .andExpect(status().is(HttpStatus.CREATED.value()))
                .andReturn();

        SquadronDto newUnit = OBJECT_MAPPER.readValue(response.getResponse().getContentAsString(), SquadronDto.class);
        UUID id = newUnit.getId();

        newUnit.setBaseName("Grissom AFB");

        MvcResult response2 = mockMvc.perform(put(ENDPOINT + id.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(newUnit)))
                .andExpect(status().is(HttpStatus.OK.value()))
                .andReturn();

        assertEquals(OBJECT_MAPPER.writeValueAsString(newUnit), response2.getResponse().getContentAsString());

        MvcResult response3 = mockMvc.perform(get(ENDPOINT + id.toString()))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(OBJECT_MAPPER.writeValueAsString(newUnit), response3.getResponse().getContentAsString());
    }

    @Transactional
    @Rollback
    @Test
    public void testUpdateBogusSquadronFails() throws Exception {

        MvcResult response = mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(squadron)))
                .andExpect(status().is(HttpStatus.CREATED.value()))
                .andReturn();

        SquadronDto newUnit = OBJECT_MAPPER.readValue(response.getResponse().getContentAsString(), SquadronDto.class);
        UUID id = UUID.randomUUID();

        newUnit.setBaseName("Grissom AFB");

        mockMvc.perform(put(ENDPOINT + id.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(newUnit)))
                .andExpect(status().is(HttpStatus.NOT_FOUND.value()));
    }

    @Transactional
    @Rollback
    @Test
    public void testUpdateSquadronDifferingIdsFails() throws Exception {

        MvcResult response = mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(squadron)))
                .andExpect(status().is(HttpStatus.CREATED.value()))
                .andReturn();

        SquadronDto newUnit = OBJECT_MAPPER.readValue(response.getResponse().getContentAsString(), SquadronDto.class);
        newUnit.setBaseName("Grissom AFB");
        UUID realId = newUnit.getId();
        newUnit.setId(UUID.randomUUID());

        // now inject a random UUID for the id, so that it and the one in the request body will be different...
        mockMvc.perform(put(ENDPOINT + realId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(newUnit)))
                .andExpect(status().is(HttpStatus.BAD_REQUEST.value()));
    }

    @Transactional
    @Rollback
    @Test
    public void testDeleteSquadron() throws Exception {

        MvcResult response = mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(squadron)))
                .andExpect(status().is(HttpStatus.CREATED.value()))
                .andReturn();

        SquadronDto newUnit = OBJECT_MAPPER.readValue(response.getResponse().getContentAsString(), SquadronDto.class);
        UUID id = newUnit.getId();

        // see how many recs are in there now
        MvcResult allRecs = mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isOk())
                .andReturn();

        SquadronDto[] allSquadronRecs = OBJECT_MAPPER.readValue(allRecs.getResponse().getContentAsString(), SquadronDto[].class);
        int totalRecs = allSquadronRecs.length;

        // delete the record
        mockMvc.perform(delete(ENDPOINT + id.toString())).andExpect(status().is(HttpStatus.OK.value()));

        // refetch all recs
        MvcResult modRecs = mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isOk())
                .andReturn();

        SquadronDto[] newAllSquadronRecs = OBJECT_MAPPER.readValue(modRecs.getResponse().getContentAsString(), SquadronDto[].class);

        // assert we have one less in the db
        assertEquals(totalRecs - 1, newAllSquadronRecs.length);
    }

    @Nested
    class TestSquadronAttributeChanges {

        private Airman newAirman;
        private SquadronDto newSquadron;

        @BeforeEach
        public void initAirmanAndSquadron() throws Exception {

            // add an airman and squadron and POST them
            Airman airman = new Airman();
            airman.setFirstName("John");
            airman.setMiddleName("Hero");
            airman.setLastName("Public");
            airman.setEmail("john@test.com");
            airman.setTitle("Capt");
            airman.setAfsc("17D");
            airman.setPtDate(new Date(2020-1900, Calendar.OCTOBER, 1));
            airman.setEtsDate(new Date(2021-1900, Calendar.JUNE, 29));

            MvcResult response = mockMvc.perform(post(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(squadron)))
                    .andExpect(status().is(HttpStatus.CREATED.value()))
                    .andReturn();

            newSquadron = OBJECT_MAPPER.readValue(response.getResponse().getContentAsString(), SquadronDto.class);
            assertNull(newSquadron.getLeader());

            MvcResult response2 = mockMvc.perform(post(AIRMAN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(airman)))
                    .andExpect(status().is(HttpStatus.CREATED.value()))
                    .andReturn();

            newAirman = OBJECT_MAPPER.readValue(response2.getResponse().getContentAsString(), Airman.class);

        }

        @Transactional
        @Rollback
        @Test
        public void testPatchAttributes() throws Exception {

            Map<String, String> attribs = new HashMap<>();
            attribs.put("leader", newAirman.getId().toString());
            attribs.put("operationsDirector", newAirman.getId().toString());
            attribs.put("chief", newAirman.getId().toString());
            attribs.put("baseName", "Grissom AFB");
            attribs.put("majorCommand", "ACC");


            for (String attrib : attribs.keySet()) {
                Map<String, String> data = new HashMap<>();

                // set attribute
                data.put(attrib, attribs.get(attrib));
                MvcResult newResponse = mockMvc.perform(patch(ENDPOINT + newSquadron.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBJECT_MAPPER.writeValueAsString(data)))
                        .andExpect(status().isOk())
                        .andReturn();

                SquadronDto newUnitMod = OBJECT_MAPPER.readValue(newResponse.getResponse().getContentAsString(), SquadronDto.class);

                if (attrib.equals("leader")) assertEquals(attribs.get(attrib), newUnitMod.getLeader().toString());
                else if (attrib.equals("operationsDirector")) assertEquals(attribs.get(attrib), newUnitMod.getOperationsDirector().toString());
                else if (attrib.equals("chief")) assertEquals(attribs.get(attrib), newUnitMod.getChief().toString());
                else if (attrib.equals("baseName")) assertEquals(attribs.get(attrib), newUnitMod.getBaseName());
                else if (attrib.equals("majorCommand")) assertEquals(attribs.get(attrib), newUnitMod.getMajorCommand());
                else throw new Exception("Unknown attribute given");

                // test we can null out the attribute
                data.put(attrib, null);
                MvcResult noLeaderResp = mockMvc.perform(patch(ENDPOINT + newSquadron.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBJECT_MAPPER.writeValueAsString(data)))
                        .andExpect(status().isOk())
                        .andReturn();

                SquadronDto clearedUnit = OBJECT_MAPPER.readValue(noLeaderResp.getResponse().getContentAsString(), SquadronDto.class);

                if (attrib.equals("leader")) assertNull(clearedUnit.getLeader());
                else if (attrib.equals("operationsDirector")) assertNull(clearedUnit.getOperationsDirector());
                else if (attrib.equals("chief")) assertNull(clearedUnit.getChief());
                else if (attrib.equals("baseName")) assertNull(clearedUnit.getBaseName());
                else if (attrib.equals("majorCommand")) assertNull(clearedUnit.getMajorCommand());
                else throw new Exception("Unknown attribute given");
            }

            // test can't change a squadron's existing id
            mockMvc.perform(patch(ENDPOINT + newSquadron.getId().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(Maps.newHashMap("id", UUID.randomUUID()))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @Transactional
        @Rollback
        public void testAddRemoveMemberToSquadron() throws Exception {

            // test the 404 - bad squadron uuid
            mockMvc.perform(patch(ENDPOINT + newSquadron.getId().toString() + "/members")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(new UUID[] { new Airman().getId() })))
                    .andExpect(status().is(HttpStatus.BAD_REQUEST.value()));

            // test the 400 - bad airmen uuid
            mockMvc.perform(patch(ENDPOINT + new SquadronDto().getId().toString() + "/members")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(new UUID[] { new Airman().getId() })))
                    .andExpect(status().is(HttpStatus.NOT_FOUND.value()));

            MvcResult result = mockMvc.perform(patch(ENDPOINT + newSquadron.getId().toString() + "/members")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(new UUID[] { newAirman.getId() })))
                    .andExpect(status().isOk())
                    .andReturn();

            SquadronDto modSquad = OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), SquadronDto.class);
            assertEquals(1, modSquad.getMembers().size());

            MvcResult result2 = mockMvc.perform(delete(ENDPOINT + newSquadron.getId().toString() + "/members")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(new UUID[] { newAirman.getId() })))
                    .andExpect(status().isOk())
                    .andReturn();

            SquadronDto modSquad2 = OBJECT_MAPPER.readValue(result2.getResponse().getContentAsString(), SquadronDto.class);
            assertEquals(0, modSquad2.getMembers().size());
        }

    }

    @Test
    @Transactional
    @Rollback
    void testBulkAddSquadrons() throws Exception {

        SquadronDto s2 = new SquadronDto();
        s2.setName("TEST2");

        SquadronDto s3 = new SquadronDto();
        s3.setName("TEST3");

        List<SquadronDto> newSquadrons = Lists.newArrayList(
                squadron,
                s2,
                s3
        );

        // test go path for controller to db for adding build squadrons, add 3 get back 3
        mockMvc.perform(post(ENDPOINT + "squadrons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(newSquadrons)))
                .andExpect(status().isCreated())
                .andExpect(result -> assertEquals(3, OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), SquadronDto[].class).length));

        // now try to add again one that already has an existing name
        SquadronDto s4 = new SquadronDto();
        s4.setName(squadron.getName());
        mockMvc.perform(post(ENDPOINT + "squadrons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(s4))))
                .andExpect(status().isConflict());
    }

    @Transactional
    @Rollback
    @Test
    public void testAddMemberToMultipleOrgs() throws Exception {

        // add the first squadron
        MvcResult response = mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(squadron)))
                .andExpect(status().is(HttpStatus.CREATED.value()))
                .andReturn();

        SquadronDto newUnit1 = OBJECT_MAPPER.readValue(response.getResponse().getContentAsString(), SquadronDto.class);

        SquadronDto s2 = new SquadronDto();
        s2.setName("Squad2");

        // add the second squadron
        MvcResult response2 = mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(s2)))
                .andExpect(status().is(HttpStatus.CREATED.value()))
                .andReturn();

        SquadronDto newUnit2 = OBJECT_MAPPER.readValue(response2.getResponse().getContentAsString(), SquadronDto.class);

        Airman airman = new Airman();
        airman.setFirstName("John");
        airman.setMiddleName("Hero");
        airman.setLastName("Public");
        airman.setEmail("john@test.com");
        airman.setTitle("Capt");
        airman.setAfsc("17D");
        airman.setPtDate(new Date(2020-1900, Calendar.OCTOBER, 1));
        airman.setEtsDate(new Date(2021-1900, Calendar.JUNE, 29));

        // add an airman
        MvcResult response3 = mockMvc.perform(post(AIRMAN_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(airman)))
                .andExpect(status().is(HttpStatus.CREATED.value()))
                .andReturn();

        Airman newAirman = OBJECT_MAPPER.readValue(response3.getResponse().getContentAsString(), Airman.class);

        // assign the airman to both squadrons
        mockMvc.perform(patch(ENDPOINT + newUnit1.getId() + "/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(new UUID[] { newAirman.getId() })))
                .andExpect(status().is(HttpStatus.OK.value()))
                .andExpect(result -> assertEquals(1, OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), SquadronDto.class)
                                        .getMembers()
                                        .stream()
                                        .filter(member -> member.equals(newAirman.getId()))
                                        .collect(Collectors.toList())
                                        .size()));

        mockMvc.perform(patch(ENDPOINT + newUnit2.getId() + "/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(new UUID[] { newAirman.getId() })))
                .andExpect(status().is(HttpStatus.OK.value()))
                .andExpect(result -> assertEquals(1, OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), SquadronDto.class)
                        .getMembers()
                        .stream()
                        .filter(member -> member.equals(newAirman.getId()))
                        .collect(Collectors.toList())
                        .size()));


        // cant add the same member more than one to the same org though....
        // add, rejects silently in that the newAirman's UUID is still only present once in the organization
        mockMvc.perform(patch(ENDPOINT + newUnit2.getId() + "/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(new UUID[] { newAirman.getId() })))
                .andExpect(status().is(HttpStatus.OK.value()))
                .andExpect(result -> assertEquals(1, OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), SquadronDto.class)
                        .getMembers()
                        .stream()
                        .filter(member -> member.equals(newAirman.getId()))
                        .collect(Collectors.toList())
                        .size()));
    }
}
