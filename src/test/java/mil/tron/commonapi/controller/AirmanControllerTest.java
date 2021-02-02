package mil.tron.commonapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.entity.Airman;
import mil.tron.commonapi.entity.ranks.AirmanRank;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.service.AirmanService;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
public class AirmanControllerTest {
    private static final String ENDPOINT = "/v1/airman/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AirmanService airmanService;

    private Airman airman;

    @BeforeEach
    public void insertAirman() throws Exception {
        airman = new Airman();
        airman.setFirstName("John");
        airman.setMiddleName("Hero");
        airman.setLastName("Public");
        airman.setEmail("john@test.com");
        airman.setTitle("CAPT");
        airman.setAfsc("17D");
        airman.setPtDate(new Date(2020-1900, Calendar.OCTOBER, 1));
        airman.setEtsDate(new Date(2021-1900, Calendar.JUNE, 29));
    }

    @Test
    public void testAddNewAirmen() throws Exception {

        Mockito.when(airmanService.createAirman(Mockito.any(Airman.class))).then(returnsFirstArg());

        MvcResult response = mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON).content(OBJECT_MAPPER.writeValueAsString(airman)))
                .andExpect(status().is(HttpStatus.CREATED.value()))
                .andReturn();

        assertEquals(OBJECT_MAPPER.writeValueAsString(airman), response.getResponse().getContentAsString());
    }

    @Test
    public void testGetAirman() throws Exception {

        Mockito.when(airmanService.getAirman(Mockito.any(UUID.class))).thenReturn(airman);

        mockMvc.perform(get(ENDPOINT + airman.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(airman)));

    }

    @Test
    public void testGetBogusAirmanFails() throws Exception {

        Mockito.when(airmanService.getAirman(Mockito.any(UUID.class))).thenThrow(new RecordNotFoundException("Record not found"));

        UUID id = UUID.randomUUID();

        mockMvc.perform(get(ENDPOINT + id.toString()))
                .andExpect(status().is(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    public void testUpdateAirman() throws Exception {
        airman.setTitle("MAJ");

        Mockito.when(airmanService.updateAirman(Mockito.any(UUID.class), Mockito.any(Airman.class)))
                .thenReturn(airman);

        MvcResult result = mockMvc.perform(put(ENDPOINT + airman.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(airman)))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(OBJECT_MAPPER.writeValueAsString(airman), result.getResponse().getContentAsString());
    }

    @Test
    public void testUpdateBogusAirmanFails() throws Exception {

        Mockito.when(airmanService.updateAirman(Mockito.any(UUID.class), Mockito.any(Airman.class)))
                .thenThrow(new RecordNotFoundException("Not found"));

        Airman newGuy = new Airman();
        newGuy.setRank(AirmanRank.MAJ);

        mockMvc.perform(put(ENDPOINT + newGuy.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(newGuy)))
                .andExpect(status().is(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    public void testUpdateAirmanDifferingIdsFails() throws Exception {

        Mockito.when(airmanService.updateAirman(Mockito.any(UUID.class), Mockito.any(Airman.class)))
                .thenThrow(new InvalidRecordUpdateRequest("IDs don't match"));

        Airman newGuy = new Airman();
        newGuy.setTitle("Maj");


        // now inject a random UUID for the id, so that it and the one in the request body will be different...
        mockMvc.perform(put(ENDPOINT + airman.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(newGuy)))
                .andExpect(status().is(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    public void testDeleteAirman() throws Exception {

        Mockito.doNothing().when(airmanService).removeAirman(airman.getId());

        // delete the record
        mockMvc.perform(delete(ENDPOINT + airman.getId().toString()))
                .andExpect(status().is(HttpStatus.OK.value()));

        // delete the record bad UUID
        mockMvc.perform(delete(ENDPOINT + "aaaadddd1122"))
                .andExpect(status().is(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    public void testBulkAddAirmen() throws Exception {

        Mockito.when(airmanService.bulkAddAirmen(Mockito.anyList())).then(returnsFirstArg());


        // delete the record
        mockMvc.perform(post(ENDPOINT + "airmen")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(airman))))
                .andExpect(status().is(HttpStatus.CREATED.value()));

    }
}
