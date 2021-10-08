package mil.tron.commonapi.controller.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import mil.tron.commonapi.dto.dashboard.AppSourceErrorResponseDto;
import mil.tron.commonapi.dto.dashboard.AppSourceErrorUsageDto;
import mil.tron.commonapi.dto.dashboard.AppSourceUsageDto;
import mil.tron.commonapi.dto.dashboard.AppSourceUsageResponseDto;
import mil.tron.commonapi.dto.dashboard.EntityAccessorDto;
import mil.tron.commonapi.dto.dashboard.EntityAccessorResponseDto;
import mil.tron.commonapi.dto.dashboard.ResponseDto;
import mil.tron.commonapi.service.dashboard.DashboardService;

@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
@AutoConfigureMockMvc
class DashboardControllerTest {
	private static final String ENDPOINT = "/v2/dashboard/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;
    
    @Test
    void getAppClientsAccessingOrganizations() throws Exception {
    	EntityAccessorDto entityAccessor = EntityAccessorDto.builder()
				.name("test accessor")
				.recordAccessCount(100L)
				.build();
		
		EntityAccessorResponseDto response = EntityAccessorResponseDto.builder()
				.startDate(new Date(1626652800000L))
				.endDate(new Date(1626739200000L))
				.entityAccessors(List.of(entityAccessor))
				.build();
		
        Mockito.when(dashboardService.getAppClientsAccessingOrgRecords(Mockito.any(), Mockito.any()))
                .thenReturn(response);
        
        mockMvc.perform(get(ENDPOINT + "app-client-organization-accessors?startDate=2021-07-19T00:00:00.000Z&endDate=2021-07-20T00:00:00.000Z"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(response)));
    }

	@Test
    void getAppClientsAccessingPersonnel() throws Exception {
    	EntityAccessorDto entityAccessor = EntityAccessorDto.builder()
				.name("test accessor")
				.recordAccessCount(100L)
				.build();
		
	    EntityAccessorResponseDto response = EntityAccessorResponseDto.builder()
				.startDate(new Date(1626652800000L))
				.endDate(new Date(1626739200000L))
				.entityAccessors(List.of(entityAccessor))
				.build();
		
        Mockito.when(dashboardService.getAppClientsAccessingPersonnelRecords(Mockito.any(), Mockito.any()))
                .thenReturn(response);
        
        mockMvc.perform(get(ENDPOINT + "app-client-personnel-accessors?startDate=2021-07-19T00:00:00.000Z&endDate=2021-07-20T00:00:00.000Z"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(response)));
    }
    
    @Test
    void getAppSourceUsageCount() throws Exception {
		AppSourceUsageDto testApp = AppSourceUsageDto.builder()
				.name("testApp")
				.incomingRequestCount(2L)
				.build();
		
		AppSourceUsageDto testApp2 = AppSourceUsageDto.builder()
				.name("testApp2")
				.incomingRequestCount(1L)
				.build();
		
		LinkedList<AppSourceUsageDto> appSourceUsageList = new LinkedList<>();
		appSourceUsageList.add(testApp);
		appSourceUsageList.add(testApp2);
		
		AppSourceUsageResponseDto response = AppSourceUsageResponseDto.builder()
				.startDate(new Date(1626652800000L))
				.endDate(new Date(1626739200000L))
				.appSourceUsage(appSourceUsageList)
				.build();
		
        Mockito.when(dashboardService.getAppSourceUsage(Mockito.any(Date.class), Mockito.nullable(Date.class), Mockito.anyBoolean(), Mockito.anyLong()))
               	.thenReturn(response);
        
        mockMvc.perform(get(ENDPOINT + "app-source-usage?startDate=2021-07-19T00:00:00.000Z&endDate=2021-07-20T00:00:00.000Z"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(response)));
    }
    
    @Test
    void getAppSourceErrorUsageCount() throws Exception {
    	AppSourceErrorUsageDto appSourceUsage = AppSourceErrorUsageDto.builder()
				.name("testApp")
				.totalErrorResponses(3L)
				.errorResponses(List.of(
							ResponseDto.builder()
								.count(2)
								.statusCode(503)
								.build(),
							ResponseDto.builder()
								.count(1)
								.statusCode(400)
								.build()
						))
				.build();
		
		AppSourceErrorResponseDto response = AppSourceErrorResponseDto.builder()
				.appSourceUsage(List.of(appSourceUsage))
				.startDate(new Date(1626652800000L))
				.endDate(new Date(1626739200000L))
				.build();

        Mockito.when(dashboardService.getAppSourceErrorUsage(Mockito.any(), Mockito.any()))
                .thenReturn(response);
        
        mockMvc.perform(get(ENDPOINT + "app-source-error-usage?startDate=2021-07-19T00:00:00.000Z&endDate=2021-07-20T00:00:00.000Z"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(response)));
    }
}
