package mil.tron.commonapi.controller;

import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.net.HttpHeaders;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.dto.LogfileDto;
import mil.tron.commonapi.exception.ExceptionResponse;
import mil.tron.commonapi.service.LogfileService;

@RestController
@RequestMapping("${api-prefix.v1}/logfile")
public class LogfileController {
	
	
	private LogfileService service;
	
	public LogfileController(LogfileService service) {
		this.service = service;
	}

	@Operation(summary = "Retrieves all logfiles info", description = "Retrieves all logfiles available for download")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
				description = "Successful operation", 
				content = @Content(array = @ArraySchema(schema = @Schema(implementation = LogfileDto.class))))
	})
	@GetMapping
	public ResponseEntity<Object> getLogfileInfo() {
		return new ResponseEntity<>(service.getAllLogfileInfo(), HttpStatus.OK);
	}
	
	@Operation(summary = "Retrieves a logfile for download", description = "Retrieves a logfile for download")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
				description = "Successful operation"),
			@ApiResponse(responseCode = "500",
				description = "I/O error",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "404",
				description = "File not found",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "400",
				description = "Bad request",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@GetMapping("/{fileName:.+}")
	public ResponseEntity<Resource> getLogfile(@PathVariable String fileName, HttpServletRequest request) {
		Resource resource = service.getLogfileResource(fileName);
			
		return ResponseEntity
			.ok()
			.contentType(MediaType.parseMediaType("application/gzip"))
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + service.getLogfileResourceName(resource) + "\"")
			.body(resource);
	}
	
}
