package mil.tron.commonapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.dto.LogfileDto;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.exception.FileNotExistsException;
import mil.tron.commonapi.service.AppClientUserPreAuthenticatedService;
import mil.tron.commonapi.service.LogfileService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
@AutoConfigureMockMvc
class LogfileControllerTest {
	private static final String ENDPOINT = "/v1/logfile/";
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	
	@Autowired
	private MockMvc mockMvc;
	
	@MockBean
	private AppClientUserPreAuthenticatedService appClientUserPreAuthenticatedService;
	
	@MockBean
	private LogfileService service;
	
	@Test
	void getLogfileInfoTest() throws Exception {
		List<LogfileDto> logfileDtos = new ArrayList<>();
		LogfileDto dto = new LogfileDto();
		dto.setName("1.gz");
		dto.setDownloadUri("http://localhost:8088/api" + ENDPOINT + "1.gz");
		logfileDtos.add(dto);
		
		Mockito.when(service.getAllLogfileInfo()).thenReturn(logfileDtos);
		
		mockMvc.perform(get(ENDPOINT))
			.andExpect(status().isOk())
			.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(logfileDtos)));
	}	
	
	@Test
	void getLogfileTest() throws Exception {
		Resource resource = new ByteArrayResource("test".getBytes());
		
		Mockito.when(service.getLogfileResource(Mockito.anyString())).thenReturn(resource);
		
		mockMvc.perform(
				get(ENDPOINT + "{logfile}", "testFile.log")
					.accept(MediaType.parseMediaType("application/gzip"))
			)
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.parseMediaType("application/gzip")));
	}	
	
	@Test
	void getLogfileTest_FileNotExists() throws Exception {
		Mockito.when(service.getLogfileResource(Mockito.anyString())).thenThrow(FileNotExistsException.class);
		
		mockMvc.perform(
				get(ENDPOINT + "{logfile}", "testFile.log")
					.accept(MediaType.parseMediaType("application/gzip"))
			)
			.andExpect(status().isNotFound());
	}	
	
	@Test
	void getLogfileTest_InvalidPathException() throws Exception {
		Mockito.when(service.getLogfileResource(Mockito.anyString())).thenThrow(BadRequestException.class);
		
		mockMvc.perform(
				get(ENDPOINT + "{logfile}", "testFile.log")
					.accept(MediaType.parseMediaType("application/gzip"))
			)
			.andExpect(status().isBadRequest());
	}	
	
}
