package mil.tron.commonapi.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.annotation.security.PreAuthorizeDashboardAdmin;
import mil.tron.commonapi.dto.PrivilegeDto;
import mil.tron.commonapi.service.PrivilegeService;

@RestController
@RequestMapping("${api-prefix.v1}/privilege")
@PreAuthorizeDashboardAdmin
public class PrivilegeController {
	
	private PrivilegeService privilegeService;
	
	public PrivilegeController(PrivilegeService privilegeService) {
		this.privilegeService = privilegeService;
	}
	
	@Operation(summary = "Retrieves all Privilege information", description = "Retrieves Privilege information")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
				description = "Successful operation", 
				content = @Content(array = @ArraySchema(schema = @Schema(implementation = PrivilegeDto.class))))
	})
	@GetMapping
	public ResponseEntity<Object> getPrivileges() {
		return new ResponseEntity<>(privilegeService.getPrivileges(), HttpStatus.OK);
	}
}
