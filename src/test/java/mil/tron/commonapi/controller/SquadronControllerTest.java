package mil.tron.commonapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.entity.Airman;
import mil.tron.commonapi.entity.Squadron;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.service.SquadronService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
public class SquadronControllerTest {
    private static final String ENDPOINT = "/v1/squadron/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SquadronService squadronService;

    private Squadron squadron;

    @BeforeEach
    public void insertSquadron() throws Exception {
        squadron = new Squadron();
        squadron.setName("TEST ORG");
        squadron.setMajorCommand("ACC");
        squadron.setBaseName("Travis AFB");
    }

    @Test
    public void testAddNewSquadron() throws Exception {

        Mockito.when(squadronService.createSquadron(Mockito.any(Squadron.class))).then(returnsFirstArg());
        MvcResult response = mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON).content(OBJECT_MAPPER.writeValueAsString(squadron)))
                .andExpect(status().is(HttpStatus.CREATED.value()))
                .andReturn();

        assertEquals(OBJECT_MAPPER.writeValueAsString(squadron), response.getResponse().getContentAsString());
    }

    @Test
    public void testAddNewSquadronOverwriteExistingFails() throws Exception {

        Mockito.when(squadronService.createSquadron(Mockito.any(Squadron.class)))
                .thenThrow(new ResourceAlreadyExistsException("Record Already Exists"));

        // this POST will fail since it'll detect UUID in db already exists
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON).content(OBJECT_MAPPER.writeValueAsString(squadron)))
                .andExpect(status().is(HttpStatus.CONFLICT.value()));

    }

    @Test
    public void testGetSquadron() throws Exception {

        Mockito.when(squadronService.getSquadron(Mockito.any(UUID.class))).thenReturn(squadron);

        MvcResult response = mockMvc.perform(get(ENDPOINT + squadron.getId().toString()))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(OBJECT_MAPPER.writeValueAsString(squadron), response.getResponse().getContentAsString());
    }

    @Test
    public void testGetBogusSquadronFails() throws Exception {

        Mockito.when(squadronService.getSquadron(Mockito.any(UUID.class)))
                .thenThrow(new RecordNotFoundException("Not found"));

        UUID id = UUID.randomUUID();

        mockMvc.perform(get(ENDPOINT + id.toString()))
                .andExpect(status().is(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    public void testUpdateSquadron() throws Exception {

        Mockito.when(squadronService.updateSquadron(Mockito.any(UUID.class), Mockito.any(Squadron.class)))
                .thenReturn(squadron);

        squadron.setBaseName("Grissom AFB");

        MvcResult response = mockMvc.perform(put(ENDPOINT + squadron.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(squadron)))
                .andExpect(status().is(HttpStatus.OK.value()))
                .andReturn();

        assertEquals(OBJECT_MAPPER.writeValueAsString(squadron), response.getResponse().getContentAsString());
    }

    @Test
    public void testUpdateBogusSquadronFails() throws Exception {

        Mockito.when(squadronService.updateSquadron(Mockito.any(UUID.class), Mockito.any(Squadron.class)))
                .thenThrow(new RecordNotFoundException("Record not found"));

        UUID id = UUID.randomUUID();

        squadron.setBaseName("Grissom AFB");

        mockMvc.perform(put(ENDPOINT + id.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(squadron)))
                .andExpect(status().is(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    public void testUpdateSquadronDifferingIdsFails() throws Exception {

        Mockito.when(squadronService.updateSquadron(Mockito.any(UUID.class), Mockito.any(Squadron.class)))
                .thenThrow(new InvalidRecordUpdateRequest("IDs are different"));

        Squadron newUnit = new Squadron();
        newUnit.setBaseName("Grissom AFB");
        UUID realId = newUnit.getId();
        newUnit.setId(UUID.randomUUID());

        // now inject a random UUID for the id, so that it and the one in the request body will be different...
        mockMvc.perform(put(ENDPOINT + realId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(newUnit)))
                .andExpect(status().is(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    public void testDeleteSquadron() throws Exception {

        doNothing().when(squadronService).removeSquadron(Mockito.any(UUID.class));

        // delete the record
        mockMvc.perform(delete(ENDPOINT + squadron.getId().toString()))
                .andExpect(status().is(HttpStatus.OK.value()));

        doThrow(new RecordNotFoundException("Record not found"))
                .when(squadronService)
                .removeSquadron(Mockito.any(UUID.class));

        // delete the record with bad ID
        mockMvc.perform(delete(ENDPOINT + squadron.getId().toString()))
                .andExpect(status().is(HttpStatus.NOT_FOUND.value()));

    }

    @Nested
    class TestSquadronAttributeChanges {

        private Airman newAirman;

        @BeforeEach
        public void initAirmanAndSquadron() throws Exception {

            newAirman = new Airman();
            newAirman.setFirstName("John");
            newAirman.setMiddleName("Hero");
            newAirman.setLastName("Public");
            newAirman.setEmail("john@test.com");
            newAirman.setTitle("Capt");
            newAirman.setAfsc("17D");
            newAirman.setPtDate(new Date(2020-1900, Calendar.OCTOBER, 1));
            newAirman.setEtsDate(new Date(2021-1900, Calendar.JUNE, 29));
        }

        @Test
        public void testPatchAttributes() throws Exception {

            Map<String, String> attribs = new HashMap<>();
            attribs.put("leader", newAirman.getId().toString());
            attribs.put("operationsDirector", newAirman.getId().toString());
            attribs.put("chief", newAirman.getId().toString());
            attribs.put("baseName", "Grissom AFB");
            attribs.put("majorCommand", "ACC");

            Mockito.when(squadronService
                    .modifySquadronAttributes(Mockito.any(UUID.class), Mockito.anyMap()))
                    .thenReturn(squadron);

            for (String attrib : attribs.keySet()) {
                Map<String, String> data = new HashMap<>();

                // set attribute
                data.put(attrib, attribs.get(attrib));
                MvcResult newResponse = mockMvc.perform(patch(ENDPOINT + squadron.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBJECT_MAPPER.writeValueAsString(data)))
                        .andExpect(status().isOk())
                        .andReturn();

            }
        }

        @Test
        public void testAddRemoveMemberToSquadron() throws Exception {

            Mockito.when(squadronService
                    .addSquadronMember(Mockito.any(UUID.class), Mockito.anyList()))
                    .thenReturn(squadron);

            Mockito.when(squadronService
                    .removeSquadronMember(Mockito.any(UUID.class), Mockito.anyList()))
                    .thenReturn(squadron);


            mockMvc.perform(patch(ENDPOINT + squadron.getId().toString() + "/members")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(new UUID[] { new Airman().getId() })))
                    .andExpect(status().isOk());

            mockMvc.perform(delete(ENDPOINT + squadron.getId().toString() + "/members")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(new UUID[] { newAirman.getId() })))
                    .andExpect(status().isOk());

        }

    }
}
