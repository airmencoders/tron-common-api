package mil.tron.commonapi.controller.kpi;

import com.fasterxml.jackson.databind.ObjectMapper;

import mil.tron.commonapi.dto.kpi.KpiSummaryDto;
import mil.tron.commonapi.dto.kpi.KpiSummaryDtoResponseWrapper;
import mil.tron.commonapi.dto.kpi.UniqueVisitorCountDto;
import mil.tron.commonapi.entity.kpi.VisitorType;
import mil.tron.commonapi.service.kpi.KpiService;

import org.junit.jupiter.api.BeforeEach;
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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@SpringBootTest
@AutoConfigureMockMvc
class KpiControllerTest {
    private static final String ENDPOINT = "/v2/kpi/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KpiService kpiService;
    
    List<UniqueVisitorCountDto> uniqueVisitorCount;
    KpiSummaryDto kpiSummaryDto;
    
    @BeforeEach
    void setup() {
    	uniqueVisitorCount = new ArrayList<>();
		uniqueVisitorCount.add(UniqueVisitorCountDto.builder()
				.visitorType(VisitorType.DASHBOARD_USER)
				.uniqueCount(4L)
				.requestCount(100L)
				.build());
		
		uniqueVisitorCount.add(UniqueVisitorCountDto.builder()
				.visitorType(VisitorType.APP_CLIENT)
				.uniqueCount(2L)
				.requestCount(10L)
				.build());
    	
    	kpiSummaryDto = KpiSummaryDto.builder()
        		.appClientToAppSourceRequestCount(10L)
        		.appSourceCount(1L)
        		.averageLatencyForSuccessfulRequests(33d)
        		.uniqueVisitorCounts(uniqueVisitorCount)
        		.build();
    }

    @Test
    void getKpiSummaryTest() throws Exception{
        Mockito.when(kpiService.aggregateKpis(Mockito.any(Date.class), Mockito.nullable(Date.class)))
                .thenReturn(kpiSummaryDto);
        mockMvc.perform(get(ENDPOINT + "summary?startDate=2021-05-27"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(kpiSummaryDto)));
    }
    
    @Test
    void getKpiSummary_shouldFail_whenBadQueryParameter() throws Exception{
        mockMvc.perform(get(ENDPOINT + "summary"))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void getKpiSeries() throws Exception {
    	List<KpiSummaryDto> response = new ArrayList<>();
    	response.add(kpiSummaryDto);
    	KpiSummaryDtoResponseWrapper wrapper = new KpiSummaryDtoResponseWrapper();
    	wrapper.setData(response);
    	
    	Mockito.when(kpiService.getKpisRangeOnStartDateBetween(Mockito.any(Date.class), Mockito.nullable(Date.class))).thenReturn(response);
    	
    	mockMvc.perform(get(ENDPOINT + "series?startDate=2021-05-27"))
	        .andExpect(status().isOk())
	        .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(wrapper)));
    }
    
    @Test
    void getKpiSeries_shouldFail_whenBadQueryParameter() throws Exception {
    	mockMvc.perform(get(ENDPOINT + "series"))
    	.andExpect(status().isBadRequest());
    }
}
