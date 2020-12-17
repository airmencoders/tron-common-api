package mil.tron.commonapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.entity.Airman;
import mil.tron.commonapi.entity.Squadron;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
public class SquadronControllerTest {
    private static final String ENDPOINT = "/v1/squadron/";
    private static final String AIRMAN_ENDPOINT = "/v1/airman/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    private Squadron squadron;

    @BeforeEach
    public void insertSquadron() throws Exception {
        squadron = new Squadron();
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
        String strSquadron = "{\"id\":null,\"name\":\"TEST ORG\",\"members\":[],\"leader\":null,\"parentOrganization\":null,\"orgType\":\"Squadron\",\"operationsDirector\":null,\"chief\":null,\"baseName\":\"Travis AFB\",\"majorCommand\":\"ACC\"}";

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

        Squadron newSquadron = OBJECT_MAPPER.readValue(response.getResponse().getContentAsString(), Squadron.class);

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

        Squadron newUnit = OBJECT_MAPPER.readValue(response.getResponse().getContentAsString(), Squadron.class);
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

        Squadron newUnit = OBJECT_MAPPER.readValue(response.getResponse().getContentAsString(), Squadron.class);
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

        Squadron newUnit = OBJECT_MAPPER.readValue(response.getResponse().getContentAsString(), Squadron.class);
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

        Squadron newUnit = OBJECT_MAPPER.readValue(response.getResponse().getContentAsString(), Squadron.class);
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

        Squadron newUnit = OBJECT_MAPPER.readValue(response.getResponse().getContentAsString(), Squadron.class);
        UUID id = newUnit.getId();

        // see how many recs are in there now
        MvcResult allRecs = mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isOk())
                .andReturn();

        Squadron[] allSquadronRecs = OBJECT_MAPPER.readValue(allRecs.getResponse().getContentAsString(), Squadron[].class);
        int totalRecs = allSquadronRecs.length;

        // delete the record
        mockMvc.perform(delete(ENDPOINT + id.toString())).andExpect(status().is(HttpStatus.OK.value()));

        // refetch all recs
        MvcResult modRecs = mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isOk())
                .andReturn();

        Squadron[] newAllSquadronRecs = OBJECT_MAPPER.readValue(modRecs.getResponse().getContentAsString(), Squadron[].class);

        // assert we have one less in the db
        assertEquals(totalRecs - 1, newAllSquadronRecs.length);
    }

    @Nested
    class TestSquadronAttributeChanges {

        private Airman newAirman;
        private Squadron newSquadron;

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

            newSquadron = OBJECT_MAPPER.readValue(response.getResponse().getContentAsString(), Squadron.class);
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

                Squadron newUnitMod = OBJECT_MAPPER.readValue(newResponse.getResponse().getContentAsString(), Squadron.class);

                if (attrib.equals("leader")) assertEquals(attribs.get(attrib), newUnitMod.getLeader().getId().toString());
                else if (attrib.equals("operationsDirector")) assertEquals(attribs.get(attrib), newUnitMod.getOperationsDirector().getId().toString());
                else if (attrib.equals("chief")) assertEquals(attribs.get(attrib), newUnitMod.getChief().getId().toString());
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

                Squadron clearedUnit = OBJECT_MAPPER.readValue(noLeaderResp.getResponse().getContentAsString(), Squadron.class);

                if (attrib.equals("leader")) assertNull(clearedUnit.getLeader());
                else if (attrib.equals("operationsDirector")) assertNull(clearedUnit.getOperationsDirector());
                else if (attrib.equals("chief")) assertNull(clearedUnit.getChief());
                else if (attrib.equals("baseName")) assertNull(clearedUnit.getBaseName());
                else if (attrib.equals("majorCommand")) assertNull(clearedUnit.getMajorCommand());
                else throw new Exception("Unknown attribute given");
            }
        }

        @Test
        @Transactional
        @Rollback
        public void testAddRemoveMemberToSquadron() throws Exception {

            MvcResult result = mockMvc.perform(patch(ENDPOINT + newSquadron.getId().toString() + "/members")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(new UUID[] { newAirman.getId() })))
                    .andExpect(status().isOk())
                    .andReturn();

            Squadron modSquad = OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), Squadron.class);
            assertEquals(1, modSquad.getMembers().size());

            MvcResult result2 = mockMvc.perform(delete(ENDPOINT + newSquadron.getId().toString() + "/members")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(new UUID[] { newAirman.getId() })))
                    .andExpect(status().isOk())
                    .andReturn();

            Squadron modSquad2 = OBJECT_MAPPER.readValue(result2.getResponse().getContentAsString(), Squadron.class);
            assertEquals(0, modSquad2.getMembers().size());
        }

    }
}
