package mil.tron.commonapi.controller.kpi;

import com.fasterxml.jackson.databind.ObjectMapper;

import mil.tron.commonapi.dto.kpi.KpiSummaryDto;
import mil.tron.commonapi.dto.kpi.UniqueVisitorCountDto;
import mil.tron.commonapi.entity.kpi.VisitorType;
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

import java.time.LocalDate;
import java.util.ArrayList;
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

    @Test
    void getKpiSummaryTest() throws Exception{
    	List<UniqueVisitorCountDto> uniqueVisitorCount = new ArrayList<>();
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
    	
    	KpiSummaryDto summary = KpiSummaryDto.builder()
        		.appClientToAppSourceRequestCount(10L)
        		.appSourceCount(1L)
        		.averageLatencyForSuccessfulRequests(33L)
        		.uniqueVisitorCounts(uniqueVisitorCount)
        		.build();
    	
        Mockito.when(kpiService.aggregateKpis(Mockito.any(LocalDate.class), Mockito.nullable(LocalDate.class)))
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
}
