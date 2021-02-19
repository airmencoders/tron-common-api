package mil.tron.commonapi.controller;

import java.util.UUID;

import javax.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.annotation.security.PreAuthorizeDashboardAdmin;
import mil.tron.commonapi.annotation.security.PreAuthorizeWrite;
import mil.tron.commonapi.dto.AppClientUserDto;
import mil.tron.commonapi.exception.ExceptionResponse;
import mil.tron.commonapi.service.AppClientUserService;

@RestController
@RequestMapping("${api-prefix.v1}/app-client")
@PreAuthorizeDashboardAdmin
public class AppClientController {
	
	private AppClientUserService userService;
	
	public AppClientController(AppClientUserService userService) {
		this.userService = userService;
	}
	
	@Operation(summary = "Retrieves all application client user information", description = "Retrieves application client user information")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
				description = "Successful operation", 
				content = @Content(array = @ArraySchema(schema = @Schema(implementation = AppClientUserDto.class))))
	})
	@GetMapping
	public ResponseEntity<Object> getAppClientUsers() {
		return new ResponseEntity<>(userService.getAppClientUsers(), HttpStatus.OK);
	}
	
	
	@Operation(summary = "Adds a App Client User", description = "Adds a App Client User")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "201",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = AppClientUserDto.class))),
			@ApiResponse(responseCode = "409",
					description = "Resource already exists with the name provided",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "400",
					description = "Bad request",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorizeWrite
	@PostMapping
	public ResponseEntity<AppClientUserDto> createAppClientUser(@Parameter(description = "App Client to create", required = true) @Valid @RequestBody AppClientUserDto appClient) {
		return new ResponseEntity<>(userService.createAppClientUser(appClient), HttpStatus.CREATED);
	}
	
	
	@Operation(summary = "Updates an existing Application Client", description = "Updates an existing Application Client")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = AppClientUserDto.class))),
			@ApiResponse(responseCode = "404",
					description = "Resource not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "400",
					description = "Bad request",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PutMapping(value = "/{id}")
	public ResponseEntity<AppClientUserDto> updateAppClient(
			@Parameter(description = "App Client ID to update", required = true) @PathVariable("id") UUID appClientId,
			@Parameter(description = "Updated person", required = true) @Valid @RequestBody AppClientUserDto appClient) {
		
		AppClientUserDto updatedAppClientUser = userService.updateAppClientUser(appClientId, appClient);
		return new ResponseEntity<>(updatedAppClientUser, HttpStatus.OK);
	}
}
