package mil.tron.commonapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.dto.metrics.*;
import mil.tron.commonapi.service.AppClientUserPreAuthenticatedService;
import mil.tron.commonapi.service.MetricService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
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

    @Nested
    class TestCountAppSource {
        private AppSourceCountMetricDto appSourceCountMetricDto;
        private EndpointCountMetricDto appEndpointCountMetricDto;
        private CountMetricDto appClientCountMetricDto;

        @BeforeEach
        public void setup() {
            appEndpointCountMetricDto = EndpointCountMetricDto.endpointCountMetricBuilder()
                .id(UUID.randomUUID())
                .path("endpoint1")
                .sum(2d)
                .method("GET")
                .build();
            appClientCountMetricDto = CountMetricDto.builder()
                .id(UUID.randomUUID())
                .path("appclient1")
                .sum(4d)
                .build();
            appSourceCountMetricDto = AppSourceCountMetricDto.builder()
                .id(UUID.randomUUID())
                .endpoints(Arrays.asList(appEndpointCountMetricDto))
                .appClients(Arrays.asList(appClientCountMetricDto))
                .name("AppSourceName")
                .build();
        }
        
        @Test
        public void getAppSourceCountTest() throws Exception {
            Mockito.when(service.getCountOfMetricsForAppSource(Mockito.any(UUID.class), Mockito.any(), Mockito.any())).thenReturn(appSourceCountMetricDto);
            
            mockMvc.perform(
                    get(ENDPOINT + "count/{id}", appSourceCountMetricDto.getId().toString())
                        .param("startDate", startDateStr)
                        .param("endDate", endDateStr)
                        .accept(MediaType.parseMediaType("application/json"))
                )
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.parseMediaType("application/json")))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(appSourceCountMetricDto)));
        }

        @Test
        public void getAppSourceCountTestStartDateEqualsEndDate400() throws Exception {
            mockMvc.perform(
                    get(ENDPOINT + "count/{id}", appSourceCountMetricDto.getId().toString())
                        .param("startDate", startDateStr)
                        .param("endDate", startDateStr)
                        .accept(MediaType.parseMediaType("application/json"))
                )
                .andExpect(status().isBadRequest());
        }
    
        @Test
        public void getAppSourceCountTestStartDateAfterEndDate400() throws Exception {
            mockMvc.perform(
                    get(ENDPOINT + "count/{id}", appSourceCountMetricDto.getId().toString())
                        .param("startDate", endDateStr)
                        .param("endDate", startDateStr)
                        .accept(MediaType.parseMediaType("application/json"))
                )
                .andExpect(status().isBadRequest());
        }
    
        @Test
        public void getAppSourceCountTestMissingStartDate400() throws Exception {
            mockMvc.perform(
                    get(ENDPOINT + "count/{id}", appSourceCountMetricDto.getId().toString())
                        .param("startDate", startDateStr)
                        .accept(MediaType.parseMediaType("application/json"))
                )
                .andExpect(status().isBadRequest());
        }
    
        @Test
        public void getAppSourceCountTestMissingEndDate400() throws Exception {
            mockMvc.perform(
                    get(ENDPOINT + "count/{id}", appSourceCountMetricDto.getId().toString())
                        .param("endDate", endDateStr)
                        .accept(MediaType.parseMediaType("application/json"))
                )
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class TestCountAppEndpoint {
        private AppEndpointCountMetricDto countMetricDto;
        private CountMetricDto appClientCountMetricDto;

        @BeforeEach
        public void setup() {
            appClientCountMetricDto = CountMetricDto.builder()
                .id(UUID.randomUUID())
                .path("appclient1")
                .sum(4d)
                .build();
            countMetricDto = AppEndpointCountMetricDto.builder()
                .id(UUID.randomUUID())
                .appClients(Arrays.asList(appClientCountMetricDto))
                .path("AppSourceName")
                .requestType("GET")
                .build();
        }
        
        @Test
        public void getEndpointCountTest() throws Exception {
            Mockito.when(service.getCountOfMetricsForEndpoint(Mockito.any(UUID.class), Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(countMetricDto);
            
            mockMvc.perform(
                    get(ENDPOINT + "count/{id}/endpoint", countMetricDto.getId().toString())
                        .param("startDate", startDateStr)
                        .param("endDate", endDateStr)
                        .param("method", "GET")
                        .param("path", "endpoint1")
                        .accept(MediaType.parseMediaType("application/json"))
                )
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.parseMediaType("application/json")))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(countMetricDto)));
        }

        @Test
        public void getEndpointCountTestStartDateEqualsEndDate400() throws Exception {
            mockMvc.perform(
                    get(ENDPOINT + "count/{id}/endpoint", countMetricDto.getId().toString())
                        .param("startDate", startDateStr)
                        .param("endDate", startDateStr)
                        .param("method", "GET")
                        .param("path", "endpoint1")
                        .accept(MediaType.parseMediaType("application/json"))
                )
                .andExpect(status().isBadRequest());
        }
    
        @Test
        public void getEndpointCountTestStartDateAfterEndDate400() throws Exception {
            mockMvc.perform(
                    get(ENDPOINT + "count/{id}/endpoint", countMetricDto.getId().toString())
                        .param("startDate", endDateStr)
                        .param("endDate", startDateStr)
                        .param("method", "GET")
                        .param("path", "endpoint1")
                        .accept(MediaType.parseMediaType("application/json"))
                )
                .andExpect(status().isBadRequest());
        }
    
        @Test
        public void getEndpointCountTestMissingStartDate400() throws Exception {
            mockMvc.perform(
                    get(ENDPOINT + "count/{id}/endpoint", countMetricDto.getId().toString())
                        .param("startDate", startDateStr)
                        .param("method", "GET")
                        .param("path", "endpoint1")
                        .accept(MediaType.parseMediaType("application/json"))
                )
                .andExpect(status().isBadRequest());
        }
    
        @Test
        public void getEndpointCountTestMissingEndDate400() throws Exception {
            mockMvc.perform(
                    get(ENDPOINT + "count/{id}/endpoint", countMetricDto.getId().toString())
                        .param("endDate", endDateStr)
                        .param("method", "GET")
                        .param("path", "endpoint1")
                        .accept(MediaType.parseMediaType("application/json"))
                )
                .andExpect(status().isBadRequest());
        }

        @Test
        public void getEndpointCountTestMissingPath400() throws Exception {
            mockMvc.perform(
                    get(ENDPOINT + "count/{id}/endpoint", countMetricDto.getId().toString())
                        .param("startDate", startDateStr)
                        .param("endDate", endDateStr)
                        .param("method", "GET")
                        .accept(MediaType.parseMediaType("application/json"))
                )
                .andExpect(status().isBadRequest());
        }

        @Test
        public void getEndpointCountTestMissingRequestMethod400() throws Exception {
            mockMvc.perform(
                    get(ENDPOINT + "count/{id}/endpoint", countMetricDto.getId().toString())
                        .param("startDate", startDateStr)
                        .param("endDate", endDateStr)
                        .param("path", "endpoint1")
                        .accept(MediaType.parseMediaType("application/json"))
                )
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class TestCountAppClientUser {
        private AppClientCountMetricDto countMetricDto;
        private EndpointCountMetricDto endpointClientCountMetricDto;

        @BeforeEach
        public void setup() {
            endpointClientCountMetricDto = EndpointCountMetricDto.endpointCountMetricBuilder()
                .id(UUID.randomUUID())
                .path("endpoint1")
                .sum(2d)
                .method("GET")
                .build();
            countMetricDto = AppClientCountMetricDto.builder()
                .id(UUID.randomUUID())
                .endpoints(Arrays.asList(endpointClientCountMetricDto))
                .name("AppSourceName")
                .build();
        }
        
        @Test
        public void getAppClientCountTest() throws Exception {
            Mockito.when(service.getCountOfMetricsForAppClient(Mockito.any(UUID.class), Mockito.anyString(), Mockito.any(), Mockito.any())).thenReturn(countMetricDto);
            
            mockMvc.perform(
                    get(ENDPOINT + "count/{id}/appclient", countMetricDto.getId().toString())
                        .param("startDate", startDateStr)
                        .param("endDate", endDateStr)
                        .param("name", "appclient1")
                        .accept(MediaType.parseMediaType("application/json"))
                )
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.parseMediaType("application/json")))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(countMetricDto)));
        }

        @Test
        public void getAppClientCountTestStartDateEqualsEndDate400() throws Exception {
            mockMvc.perform(
                    get(ENDPOINT + "count/{id}/appclient", countMetricDto.getId().toString())
                        .param("startDate", startDateStr)
                        .param("endDate", startDateStr)
                        .param("name", "appclient1")
                        .accept(MediaType.parseMediaType("application/json"))
                )
                .andExpect(status().isBadRequest());
        }
    
        @Test
        public void getAppClientCountTestStartDateAfterEndDate400() throws Exception {
            mockMvc.perform(
                    get(ENDPOINT + "count/{id}/appclient", countMetricDto.getId().toString())
                        .param("startDate", endDateStr)
                        .param("endDate", startDateStr)
                        .param("name", "appclient1")
                        .accept(MediaType.parseMediaType("application/json"))
                )
                .andExpect(status().isBadRequest());
        }
    
        @Test
        public void getAppClientCountTestMissingStartDate400() throws Exception {
            mockMvc.perform(
                    get(ENDPOINT + "count/{id}/appclient", countMetricDto.getId().toString())
                        .param("startDate", startDateStr)
                        .param("name", "appclient1")
                        .accept(MediaType.parseMediaType("application/json"))
                )
                .andExpect(status().isBadRequest());
        }
    
        @Test
        public void getAppClientCountTestMissingEndDate400() throws Exception {
            mockMvc.perform(
                    get(ENDPOINT + "count/{id}/appclient", countMetricDto.getId().toString())
                        .param("endDate", endDateStr)
                        .param("name", "appclient1")
                        .accept(MediaType.parseMediaType("application/json"))
                )
                .andExpect(status().isBadRequest());
        }

        @Test
        public void getAppClientCountTestMissingPath400() throws Exception {
            mockMvc.perform(
                    get(ENDPOINT + "count/{id}/appclient", countMetricDto.getId().toString())
                        .param("startDate", startDateStr)
                        .param("endDate", endDateStr)
                        .accept(MediaType.parseMediaType("application/json"))
                )
                .andExpect(status().isBadRequest());
        }
    }
}
