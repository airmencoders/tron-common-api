package mil.tron.commonapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.dto.HttpLogDtoPaginationResponseWrapper;
import mil.tron.commonapi.service.trace.HttpTraceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles(value = { "test", "development" })
@AutoConfigureMockMvc
public class HttpLogsControllerTest {

    private static final String ENDPOINT = "/v2/logs";

    @Autowired
    private MockMvc mockMvc;

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

        MvcResult result = mockMvc.perform(get(ENDPOINT + "?fromDate=2020-05-01T12:00:00"))
                .andExpect(status().isOk())
                .andReturn();

        UUID id = new ObjectMapper().readValue(result.getResponse().getContentAsString(),
                HttpLogDtoPaginationResponseWrapper.class).getData().get(0).getId();

        mockMvc.perform(get(ENDPOINT + "/{id}", id))
                .andExpect(status().isOk());

    }
}
