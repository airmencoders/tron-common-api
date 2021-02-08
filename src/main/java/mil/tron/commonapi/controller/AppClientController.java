package mil.tron.commonapi.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.annotation.security.PreAuthorizeDashboardAdmin;
import mil.tron.commonapi.dto.AppClientUserDto;
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
				content = @Content(schema = @Schema(implementation = AppClientUserDto.class)))
	})
	@GetMapping
	public ResponseEntity<Object> getUsers() {
		return new ResponseEntity<>(userService.getAppClientUsers(), HttpStatus.OK);
	}
}
