package mil.tron.commonapi.controller.kpi;

import com.fasterxml.jackson.databind.ObjectMapper;

import mil.tron.commonapi.dto.kpi.KpiSummaryDto;
import mil.tron.commonapi.dto.kpi.UniqueVisitorSummaryDto;
import mil.tron.commonapi.service.kpi.KpiService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Date;

@SpringBootTest
@AutoConfigureMockMvc
class KpiControllerTest {
    private static final String ENDPOINT = "/v2/kpi/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KpiService kpiService;

    @Test
    void getKpiSummaryTest() throws Exception{
    	KpiSummaryDto summary = KpiSummaryDto.builder()
        		.appClientToAppSourceRequestCount(10L)
        		.appSourceCount(1L)
        		.averageLatencyForSuccessfulRequests(33L)
        		.uniqueVisitorySummary(UniqueVisitorSummaryDto.builder()
        				.appClientCount(2L)
        				.appClientRequestCount(10L)
        				.dashboardUserCount(4L)
        				.dashboardUserRequestCount(100L)
        				.build()
    				)
        		.build();
    	
        Mockito.when(kpiService.aggregateKpis(Mockito.any(Date.class), Mockito.any(Date.class)))
                .thenReturn(summary);
        mockMvc.perform(get(ENDPOINT + "summary?startDate=2021-05-27"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(summary)));
    }
    
    @Test
    void getKpiSummary_ShouldFailOnBadParameter_Test() throws Exception{
        mockMvc.perform(get(ENDPOINT + "summary"))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void getKpiSummary_ShouldFailOnEndDateLessThanStartDate_Test() throws Exception{
        mockMvc.perform(get(ENDPOINT + "summary?startDate=2021-05-27&endDate=2021-04-11"))
                .andExpect(status().isBadRequest());
    }
}
