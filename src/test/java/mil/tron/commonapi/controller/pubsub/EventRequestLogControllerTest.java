package mil.tron.commonapi.controller.pubsub;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

import mil.tron.commonapi.dto.FilterDto;
import mil.tron.commonapi.dto.appclient.AppClientSummaryDto;
import mil.tron.commonapi.dto.pubsub.log.EventRequestLogDto;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import mil.tron.commonapi.repository.filter.FilterCondition;
import mil.tron.commonapi.repository.filter.FilterCriteria;
import mil.tron.commonapi.repository.filter.QueryOperator;
import mil.tron.commonapi.service.pubsub.log.EventRequestService;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
@AutoConfigureMockMvc
class EventRequestLogControllerTest {
	private static final String ENDPOINT_V2 = "/v2/event-request-log/";
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private EventRequestService eventRequestService;
    
    private EventRequestLogDto testDto;
    
    @BeforeEach
    void setup() {
    	testDto = EventRequestLogDto.builder()
    			.appClientUser(AppClientSummaryDto
    					.builder()
    					.id(UUID.randomUUID())
    					.name("Test App Client")
    					.build())
    			.eventCount(10L)
    			.eventType(EventType.ORGANIZATION_CHANGE)
    			.lastAttempted(new Date())
    			.reason("Success")
    			.wasSuccessful(true)
    			.build();
    }
    
    /*
     * By ID endpoint tests
     */
    @Test
    void getEventRequestLogsByAppClientId_shouldReturn_whenSuccess() throws Exception {
    	var page = PageRequest.of(0, 1000);
    	var dtoList = Lists.newArrayList(testDto);
    	
    	Mockito.when(eventRequestService.getByAppIdPaged(Mockito.eq(page), Mockito.any()))
			.thenReturn(new PageImpl<>(dtoList, page, dtoList.size()));
    	
    	mockMvc.perform(get(ENDPOINT_V2 + "app-client/id/{appId}", testDto.getAppClientUser().getId())
				.param("page", "0")
				.param("size", "1000"))
				.andExpect(status().isOk());
    }
    
    @Test
    void getEventRequestLogsByAppClientId_shouldThrow_whenBadIdParam() throws Exception {
    	mockMvc.perform(get(ENDPOINT_V2 + "app-client/id/{appId}", "bad param")
				.param("page", "0")
				.param("size", "1000"))
				.andExpect(status().isBadRequest());
    }
    
    @Test
    void getEventRequestLogsByAppClientIdWithFilter_shouldReturn_whenSuccess() throws JsonProcessingException, Exception {
    	var page = PageRequest.of(0, 1000);
    	var dtoList = Lists.newArrayList(testDto);
    	
    	FilterDto filterDto = new FilterDto();
		FilterCriteria criteria = FilterCriteria.builder()
				.field("wasSuccessful")
				.conditions(List.of(
						FilterCondition.builder()
							.operator(QueryOperator.EQUALS)
							.value("true")
							.build())
					)
				.build();
		
		filterDto.setFilterCriteria(Lists.newArrayList(criteria));
		
		Mockito.when(eventRequestService.getByAppIdPagedWithSpec(Mockito.eq(page), Mockito.any(), Mockito.anyList()))
			.thenReturn(new PageImpl<>(dtoList, page, dtoList.size()));
		
    	mockMvc.perform(post(ENDPOINT_V2 + "app-client/id/{appId}/filter", testDto.getAppClientUser().getId())
				.accept(MediaType.APPLICATION_JSON)
				.contentType(MediaType.APPLICATION_JSON)
				.content(OBJECT_MAPPER.writeValueAsString(filterDto))
				.param("page", "0")
				.param("size", "1000"))
				.andExpect(status().isOk());
    }
    
    @Test
    void getEventRequestLogsByAppClientIdWithFilter_shouldThrow_whenEmptyFilterBody() throws Exception {
    	mockMvc.perform(post(ENDPOINT_V2 + "app-client/id/{appId}/filter", testDto.getAppClientUser().getId())
				.accept(MediaType.APPLICATION_JSON)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest());
    }
    
    
    @Test
    void getEventRequestLogsByAppClientIdWithFilter_shouldThrow_whenBadIdParam() throws Exception {
    	mockMvc.perform(post(ENDPOINT_V2 + "app-client/id/{appId}/filter", "bad param")
		    	.accept(MediaType.APPLICATION_JSON)
				.contentType(MediaType.APPLICATION_JSON)
				.content(""))
				.andExpect(status().isBadRequest());
    }
    
    /*
     * ALL endpoint tests
     */
    
    @Test
    void getAllEventRequestLogs_shouldReturn_whenSuccess() throws Exception {
    	var page = PageRequest.of(0, 1000);
    	var dtoList = Lists.newArrayList(testDto);
    	
    	Mockito.when(eventRequestService.getAllPaged(page))
    		.thenReturn(new PageImpl<>(dtoList, page, dtoList.size()));
    	
    	mockMvc.perform(get(ENDPOINT_V2 + "all")
				.param("page", "0")
				.param("size", "1000"))
				.andExpect(status().isOk());
    }
    
    @Test
    void getAllEventRequestLogsWithFilter_shouldReturn_whenSuccess() throws JsonProcessingException, Exception {
    	var page = PageRequest.of(0, 1000);
    	var dtoList = Lists.newArrayList(testDto);
    	
    	FilterDto filterDto = new FilterDto();
		FilterCriteria criteria = FilterCriteria.builder()
				.field("wasSuccessful")
				.conditions(List.of(
						FilterCondition.builder()
							.operator(QueryOperator.EQUALS)
							.value("true")
							.build())
					)
				.build();
		
		filterDto.setFilterCriteria(Lists.newArrayList(criteria));
		
		Mockito.when(eventRequestService.getAllPagedWithSpec(Mockito.eq(page), Mockito.anyList()))
			.thenReturn(new PageImpl<>(dtoList, page, dtoList.size()));
		
    	mockMvc.perform(post(ENDPOINT_V2 + "all/filter", testDto.getAppClientUser().getId())
				.accept(MediaType.APPLICATION_JSON)
				.contentType(MediaType.APPLICATION_JSON)
				.content(OBJECT_MAPPER.writeValueAsString(filterDto))
				.param("page", "0")
				.param("size", "1000"))
				.andExpect(status().isOk());
    }
    
    @Test
    void getAllEventRequestLogsWithFilter_shouldThrow_whenEmptyFilterBody() throws Exception {
    	mockMvc.perform(post(ENDPOINT_V2 + "all/filter", testDto.getAppClientUser().getId())
				.accept(MediaType.APPLICATION_JSON)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest());
    }
    
    /*
     * SELF endpoint tests
     */
    
    @Test
    void getEventRequestLogsByAppClient_shouldReturn_whenSuccess() throws Exception {
    	var page = PageRequest.of(0, 1000);
    	var dtoList = Lists.newArrayList(testDto);
    	
    	Mockito.when(eventRequestService.getByAppNamePaged(Mockito.eq(page), Mockito.any()))
			.thenReturn(new PageImpl<>(dtoList, page, dtoList.size()));
    	
    	mockMvc.perform(get(ENDPOINT_V2 + "app-client/self", testDto.getAppClientUser().getId())
				.param("page", "0")
				.param("size", "1000"))
				.andExpect(status().isOk());
    }
    
    @Test
    void getEventRequestLogsByAppClientWithFilter_shouldReturn_whenSuccess() throws JsonProcessingException, Exception {
    	var page = PageRequest.of(0, 1000);
    	var dtoList = Lists.newArrayList(testDto);
    	
    	FilterDto filterDto = new FilterDto();
		FilterCriteria criteria = FilterCriteria.builder()
				.field("wasSuccessful")
				.conditions(List.of(
						FilterCondition.builder()
							.operator(QueryOperator.EQUALS)
							.value("true")
							.build())
					)
				.build();
		
		filterDto.setFilterCriteria(Lists.newArrayList(criteria));
		
		Mockito.when(eventRequestService.getByAppNamePagedWithSpec(Mockito.eq(page), Mockito.any(), Mockito.anyList()))
			.thenReturn(new PageImpl<>(dtoList, page, dtoList.size()));
		
    	mockMvc.perform(post(ENDPOINT_V2 + "app-client/self/filter", testDto.getAppClientUser().getId())
				.accept(MediaType.APPLICATION_JSON)
				.contentType(MediaType.APPLICATION_JSON)
				.content(OBJECT_MAPPER.writeValueAsString(filterDto))
				.param("page", "0")
				.param("size", "1000"))
				.andExpect(status().isOk());
    }
    
    @Test
    void getEventRequestLogsByAppClientWithFilter_shouldThrow_whenEmptyFilterBody() throws Exception {
    	mockMvc.perform(post(ENDPOINT_V2 + "app-client/self/filter", testDto.getAppClientUser().getId())
				.accept(MediaType.APPLICATION_JSON)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest());
    }
}
