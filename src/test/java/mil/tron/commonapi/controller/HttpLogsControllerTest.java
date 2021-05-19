package mil.tron.commonapi.controller;

import mil.tron.commonapi.dto.HttpLogEntryDetailsDto;
import mil.tron.commonapi.service.trace.HttpTraceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles(value = { "test", "development" })
@AutoConfigureMockMvc
public class HttpLogsControllerTest {

    private static final String ENDPOINT = "/v1/logs";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    HttpTraceService httpTraceService;

    @Test
    void testGetLogs() throws Exception {

        // no query args is a bad request
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isBadRequest());

        // malformed date/time
        mockMvc.perform(get(ENDPOINT + "?fromDate=2020"))
                .andExpect(status().isBadRequest());

        // blank query
        mockMvc.perform(get(ENDPOINT + "?fromDate="))
                .andExpect(status().isBadRequest());

        Mockito.when(httpTraceService.getLogsFromDate(
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.anyInt(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()))
                .thenReturn(new ArrayList<>());

        mockMvc.perform(get(ENDPOINT + "?fromDate=2020-05-01T12:00:00"))
                .andExpect(status().isOk());

    }

    @Test
    void getLogDetails() throws Exception {

        Mockito.when(httpTraceService.getLogInfoDetails(Mockito.any()))
                .thenReturn(new HttpLogEntryDetailsDto());

        mockMvc.perform(get(ENDPOINT + "/{id}", UUID.randomUUID()))
                .andExpect(status().isOk());
    }

}
