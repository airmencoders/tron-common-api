package mil.tron.commonapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.airman.Airman;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
public class AirmanControllerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    private Airman airman;

    @BeforeEach
    public void insertAirman() throws Exception {
        airman = new Airman();
        airman.setFirstName("John");
        airman.setMiddleName("Hero");
        airman.setLastName("Public");
        airman.setEmail("john@test.com");
        airman.setTitle("Capt");
        airman.setAfsc("17D");
        airman.setPtDate(new Date(2020-1900, Calendar.OCTOBER, 1));
        airman.setEtsDate(new Date(2021-1900, Calendar.JUNE, 29));
    }

    @Transactional
    @Rollback
    @Test
    public void testAddNewAirmen() throws Exception {

        MvcResult response = mockMvc.perform(post("/airman")
                .contentType(MediaType.APPLICATION_JSON).content(OBJECT_MAPPER.writeValueAsString(airman)))
                .andExpect(status().is(HttpStatus.CREATED.value()))
                .andReturn();

        assertEquals(OBJECT_MAPPER.writeValueAsString(airman), response.getResponse().getContentAsString());
    }

    @Transactional
    @Rollback
    @Test
    public void testGetAirman() throws Exception {

        MvcResult response = mockMvc.perform(post("/airman")
                .contentType(MediaType.APPLICATION_JSON).content(OBJECT_MAPPER.writeValueAsString(airman)))
                .andExpect(status().is(HttpStatus.CREATED.value()))
                .andReturn();

        Airman newGuy = OBJECT_MAPPER.readValue(response.getResponse().getContentAsString(), Airman.class);
        UUID id = newGuy.getId();

        MvcResult response2 = mockMvc.perform(get("/airman/" + id.toString()))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(OBJECT_MAPPER.writeValueAsString(newGuy), response2.getResponse().getContentAsString());
    }

    @Transactional
    @Rollback
    @Test
    public void testGetBogusAirmanFails() throws Exception {

        mockMvc.perform(post("/airman")
                .contentType(MediaType.APPLICATION_JSON).content(OBJECT_MAPPER.writeValueAsString(airman)))
                .andExpect(status().is(HttpStatus.CREATED.value()));

        UUID id = UUID.randomUUID();

        mockMvc.perform(get("/airman/" + id.toString()))
                .andExpect(status().is(HttpStatus.NOT_FOUND.value()));
    }

    @Transactional
    @Rollback
    @Test
    public void testUpdateAirman() throws Exception {

        MvcResult response = mockMvc.perform(post("/airman")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(airman)))
                .andExpect(status().is(HttpStatus.CREATED.value()))
                .andReturn();

        Airman newGuy = OBJECT_MAPPER.readValue(response.getResponse().getContentAsString(), Airman.class);
        UUID id = newGuy.getId();

        newGuy.setTitle("Maj");

        MvcResult response2 = mockMvc.perform(put("/airman/" + id.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(newGuy)))
                .andExpect(status().is(HttpStatus.OK.value()))
                .andReturn();

        assertEquals(OBJECT_MAPPER.writeValueAsString(newGuy), response2.getResponse().getContentAsString());

        MvcResult response3 = mockMvc.perform(get("/airman/" + id.toString()))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(OBJECT_MAPPER.writeValueAsString(newGuy), response3.getResponse().getContentAsString());
    }

    @Transactional
    @Rollback
    @Test
    public void testUpdateBogusAirmanFails() throws Exception {

        MvcResult response = mockMvc.perform(post("/airman")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(airman)))
                .andExpect(status().is(HttpStatus.CREATED.value()))
                .andReturn();

        Airman newGuy = OBJECT_MAPPER.readValue(response.getResponse().getContentAsString(), Airman.class);
        UUID id = UUID.randomUUID();

        newGuy.setTitle("Maj");

        mockMvc.perform(put("/airman/" + id.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(newGuy)))
                .andExpect(status().is(HttpStatus.NOT_FOUND.value()));
    }

    @Transactional
    @Rollback
    @Test
    public void testUpdateAirmanDifferingIdsFails() throws Exception {

        MvcResult response = mockMvc.perform(post("/airman")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(airman)))
                .andExpect(status().is(HttpStatus.CREATED.value()))
                .andReturn();

        Airman newGuy = OBJECT_MAPPER.readValue(response.getResponse().getContentAsString(), Airman.class);
        newGuy.setTitle("Maj");

        // now inject a random UUID for the id, so that it and the one in the request body will be different...
        mockMvc.perform(put("/airman/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(newGuy)))
                .andExpect(status().is(HttpStatus.NOT_FOUND.value()));
    }

    @Transactional
    @Rollback
    @Test
    public void testDeleteAirman() throws Exception {

        MvcResult response = mockMvc.perform(post("/airman")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(airman)))
                .andExpect(status().is(HttpStatus.CREATED.value()))
                .andReturn();

        Airman newGuy = OBJECT_MAPPER.readValue(response.getResponse().getContentAsString(), Airman.class);
        UUID id = newGuy.getId();

        // see how many recs are in there now
        MvcResult allRecs = mockMvc.perform(get("/airman"))
                .andExpect(status().isOk())
                .andReturn();

        Airman[] allAirmanRecs = OBJECT_MAPPER.readValue(allRecs.getResponse().getContentAsString(), Airman[].class);
        int totalRecs = allAirmanRecs.length;

        // delete the record
        mockMvc.perform(delete("/airman/" + id.toString())).andExpect(status().is(HttpStatus.NO_CONTENT.value()));

        // refetch all recs
        MvcResult modRecs = mockMvc.perform(get("/airman"))
                .andExpect(status().isOk())
                .andReturn();

        Airman[] newAllAirmanRecs = OBJECT_MAPPER.readValue(modRecs.getResponse().getContentAsString(), Airman[].class);

        // assert we have one less in the db
        assertEquals(totalRecs - 1, newAllAirmanRecs.length);
    }

}
