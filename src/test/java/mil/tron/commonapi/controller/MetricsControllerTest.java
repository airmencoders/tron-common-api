package mil.tron.commonapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import mil.tron.commonapi.dto.metrics.AppSourceMetricDto;
import mil.tron.commonapi.dto.metrics.EndpointMetricDto;
import mil.tron.commonapi.service.AppClientUserPreAuthenticatedService;
import mil.tron.commonapi.service.MetricService;

@WebMvcTest(MetricsController.class)
@WithMockUser(username = "DashboardAdminUser", authorities = { "DASHBOARD_ADMIN", "DASHBOARD_USER" })
public class MetricsControllerTest {
    private static final String ENDPOINT = "/v1/metrics/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final String startDateStr = "2021-04-09T18:30:30.155-00:00";
    private final String endDateStr = "2021-04-09T19:30:30.155-00:00";
    private EndpointMetricDto endpointMetricDto;

    @Autowired
	private MockMvc mockMvc;
	
	@MockBean
	private AppClientUserPreAuthenticatedService appClientUserPreAuthenticatedService;
	
	@MockBean
	private MetricService service;    
    
    @Nested
    class TestEndpoint {
        @BeforeEach
        public void setup() {
            endpointMetricDto = EndpointMetricDto.builder()
                .id(UUID.randomUUID())
                .path("path")
                .values(new ArrayList<>())
                .build();
        }
        
        @Test
        public void getEndpointTest() throws Exception {
            Mockito.when(service.getAllMetricsForEndpointDto(Mockito.any(UUID.class), Mockito.any(), Mockito.any())).thenReturn(endpointMetricDto);
            
            mockMvc.perform(
                    get(ENDPOINT + "endpoint/{id}", endpointMetricDto.getId().toString())
                        .param("startDate", startDateStr)
                        .param("endDate", endDateStr)
                        .accept(MediaType.parseMediaType("application/json"))
                )
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.parseMediaType("application/json")))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(endpointMetricDto)));
        }
    
        @Test
        public void getEndpointTestStartDateEqualsEndDate400() throws Exception {
            mockMvc.perform(
                    get(ENDPOINT + "endpoint/{id}", endpointMetricDto.getId().toString())
                        .param("startDate", startDateStr)
                        .param("endDate", startDateStr)
                        .accept(MediaType.parseMediaType("application/json"))
                )
                .andExpect(status().isBadRequest());
        }
    
        @Test
        public void getEndpointTestStartDateAfterEndDate400() throws Exception {
            mockMvc.perform(
                    get(ENDPOINT + "endpoint/{id}", endpointMetricDto.getId().toString())
                        .param("startDate", endDateStr)
                        .param("endDate", startDateStr)
                        .accept(MediaType.parseMediaType("application/json"))
                )
                .andExpect(status().isBadRequest());
        }
    
        @Test
        public void getEndpointTestMissingStartDate400() throws Exception {
            mockMvc.perform(
                    get(ENDPOINT + "endpoint/{id}", endpointMetricDto.getId().toString())
                        .param("startDate", startDateStr)
                        .accept(MediaType.parseMediaType("application/json"))
                )
                .andExpect(status().isBadRequest());
        }
    
        @Test
        public void getEndpointTestMissingEndDate400() throws Exception {
            mockMvc.perform(
                    get(ENDPOINT + "endpoint/{id}", endpointMetricDto.getId().toString())
                        .param("endDate", endDateStr)
                        .accept(MediaType.parseMediaType("application/json"))
                )
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class TestAppSource {
        private AppSourceMetricDto appSourceMetricDto;

        @BeforeEach
        public void setup() {
            endpointMetricDto = EndpointMetricDto.builder()
                .id(UUID.randomUUID())
                .path("path")
                .values(new ArrayList<>())
                .build();
            appSourceMetricDto = AppSourceMetricDto.builder()
                .id(UUID.randomUUID())
                .endpoints(Arrays.asList(endpointMetricDto))
                .name("AppSourceName")
                .build();
        }

        @Test
        public void getAppSourceTest() throws Exception {
            Mockito.when(service.getMetricsForAppSource(Mockito.any(UUID.class), Mockito.any(), Mockito.any())).thenReturn(appSourceMetricDto);
            
            mockMvc.perform(
                    get(ENDPOINT + "appsource/{id}", appSourceMetricDto.getId().toString())
                        .param("startDate", startDateStr)
                        .param("endDate", endDateStr)
                        .accept(MediaType.parseMediaType("application/json"))
                )
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.parseMediaType("application/json")))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(appSourceMetricDto)));
        }

        @Test
        public void getAppSourceTestStartDateEqualsEndDate400() throws Exception {
            mockMvc.perform(
                    get(ENDPOINT + "appsource/{id}", appSourceMetricDto.getId().toString())
                        .param("startDate", startDateStr)
                        .param("endDate", startDateStr)
                        .accept(MediaType.parseMediaType("application/json"))
                )
                .andExpect(status().isBadRequest());
        }
    
        @Test
        public void getAppSourceTestStartDateAfterEndDate400() throws Exception {
            mockMvc.perform(
                    get(ENDPOINT + "appsource/{id}", appSourceMetricDto.getId().toString())
                        .param("startDate", endDateStr)
                        .param("endDate", startDateStr)
                        .accept(MediaType.parseMediaType("application/json"))
                )
                .andExpect(status().isBadRequest());
        }
    
        @Test
        public void getAppSourceTestMissingStartDate400() throws Exception {
            mockMvc.perform(
                    get(ENDPOINT + "appsource/{id}", appSourceMetricDto.getId().toString())
                        .param("startDate", startDateStr)
                        .accept(MediaType.parseMediaType("application/json"))
                )
                .andExpect(status().isBadRequest());
        }
    
        @Test
        public void getAppSourceTestMissingEndDate400() throws Exception {
            mockMvc.perform(
                    get(ENDPOINT + "appsource/{id}", appSourceMetricDto.getId().toString())
                        .param("endDate", endDateStr)
                        .accept(MediaType.parseMediaType("application/json"))
                )
                .andExpect(status().isBadRequest());
        }
    }

}
