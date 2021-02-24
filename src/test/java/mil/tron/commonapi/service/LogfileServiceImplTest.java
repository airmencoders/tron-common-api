package mil.tron.commonapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import mil.tron.commonapi.dto.LogfileDto;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.exception.FileCompressionException;
import mil.tron.commonapi.exception.FileNotExistsException;


@ExtendWith(MockitoExtension.class)
class LogfileServiceImplTest {
	private static final String ENDPOINT_PREFIX = "/v1";
	
	private LogfileServiceImpl service;
	
	@TempDir 
	Path tempDir;
	
	@BeforeEach
	void setup() {
		service = new LogfileServiceImpl(tempDir.toString(), ENDPOINT_PREFIX);
		
		HttpServletRequest mockRequest = new MockHttpServletRequest();
	    ServletRequestAttributes servletRequestAttributes = new ServletRequestAttributes(mockRequest);
	    RequestContextHolder.setRequestAttributes(servletRequestAttributes);
	}

	@AfterEach
	public void teardown() {
	    RequestContextHolder.resetRequestAttributes();
	}
	
	@Test
	void getAllLogfileInfo_Test() throws IOException {
		Path test1 = tempDir.resolve("1.txt");
		Path test2 = tempDir.resolve("2.txt");
		Files.write(test1, "test 1".getBytes());
		Files.write(test2, "test 2".getBytes());
		
		Iterable<LogfileDto> dtos = service.getAllLogfileInfo();
		
		assertThat(dtos).isNotEmpty();
	}
	
	@Test
	void getAllLogfileInfo_NoFiles() throws IOException {
		Iterable<LogfileDto> dtos = service.getAllLogfileInfo();
		
		assertThat(dtos).isEmpty();
	}
	
	@Test
	void getLogfileResource_FileNotExists() throws Exception {
		assertThrows(FileNotExistsException.class, () -> service.getLogfileResource("asdf.txt"));
	}
	
	@Test
	void getLogfileResource_CurrentLog() throws Exception {
		String fileName = "spring.log";
		Path currentSpringLog = tempDir.resolve(fileName);
		Files.write(currentSpringLog, "test".getBytes());
		
		Resource resource = service.getLogfileResource(fileName);
		
		assertThat(resource).isNotNull();
		assertThat(resource.isReadable()).isTrue();
		assertThat(resource.isFile()).isFalse();
	}
	
	@Test
	void getLogfileResource_PastLog() throws Exception {
		String fileName = "spring.log.gz";
		Path currentSpringLog = tempDir.resolve(fileName);
		Files.write(currentSpringLog, "test".getBytes());
		
		Resource resource = service.getLogfileResource(fileName);
		
		assertThat(resource).isNotNull();
		assertThat(resource.isReadable()).isTrue();
		assertThat(resource.isFile()).isTrue();
		assertThat(resource.getFilename()).isEqualTo(fileName);
	}
	
	@Test
	void getLogfileResourceName_CurrentLog() throws Exception {
		Resource resource = new ByteArrayResource("Test".getBytes());
		String resourceName = service.getLogfileResourceName(resource);
		
		assertThat(resourceName).contains(".gz");
	}
	
	@Test
	void getLogfileResourceName_PastLog() throws Exception {
		String fileName = "spring.log.gz";
		Path currentSpringLog = tempDir.resolve(fileName);
		Files.write(currentSpringLog, "test".getBytes());
		
		Resource resource = new UrlResource(currentSpringLog.toUri());
		String resourceName = service.getLogfileResourceName(resource);
		
		assertThat(resourceName).isEqualTo(fileName);
	}
	
	@Test
	void createUrlResourceFromPathString() {
		assertThrows(BadRequestException.class, () -> service.createUrlResourceFromPathString("ht://localhost"));
	}
	
	@Test
	void gzipFile_InvalidPath() {
		assertThrows(FileCompressionException.class, () -> service.gzipFileAsBytes(tempDir.resolve("fakepath.txt")));
	}
	
}
