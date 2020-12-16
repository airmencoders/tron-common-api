package mil.tron.commonapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.airman.Airman;
import mil.tron.commonapi.squadron.Squadron;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

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
        String strSquadron = "{\"id\":null,\"firstName\":\"John\",\"middleName\":\"Hero\",\"lastName\":\"Public\",\"title\":\"Capt\",\"email\":\"john@test.com\",\"afsc\":\"17D\",\"etsDate\":\"2021-06-29\",\"ptDate\":\"2020-10-01\",\"fullName\":\"John Public\"}";

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

    @Transactional
    @Rollback
    @Test
    public void testPatchLeader() throws Exception {

        MvcResult response = mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(squadron)))
                .andExpect(status().is(HttpStatus.CREATED.value()))
                .andReturn();

        Squadron newUnit = OBJECT_MAPPER.readValue(response.getResponse().getContentAsString(), Squadron.class);
        assertNull(newUnit.getLeader());

        UUID id = newUnit.getId();

        Airman airman = new Airman();
        airman.setFirstName("John");
        airman.setMiddleName("Hero");
        airman.setLastName("Public");
        airman.setEmail("john@test.com");
        airman.setTitle("Capt");
        airman.setAfsc("17D");
        airman.setPtDate(new Date(2020-1900, Calendar.OCTOBER, 1));
        airman.setEtsDate(new Date(2021-1900, Calendar.JUNE, 29));

        MvcResult newAirman = mockMvc.perform(post(AIRMAN_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(airman)))
                .andExpect(status().is(HttpStatus.CREATED.value()))
                .andReturn();
        UUID airmanId = OBJECT_MAPPER.readValue(newAirman.getResponse().getContentAsString(), Airman.class).getId();

        MvcResult newResponse = mockMvc.perform(patch(ENDPOINT + id.toString() + "/leader/" + airmanId))
                .andExpect(status().isOk())
                .andReturn();

        Squadron newUnitMod = OBJECT_MAPPER.readValue(newResponse.getResponse().getContentAsString(), Squadron.class);
        assertEquals(airmanId.toString(), newUnitMod.getLeader().getId().toString());

    }
}
