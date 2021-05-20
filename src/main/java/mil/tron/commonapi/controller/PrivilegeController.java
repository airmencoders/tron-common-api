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
import mil.tron.commonapi.annotation.response.WrappedEnvelopeResponse;
import mil.tron.commonapi.annotation.security.PreAuthorizeDashboardAdmin;
import mil.tron.commonapi.dto.PrivilegeDto;
import mil.tron.commonapi.dto.PrivilegeDtoResponseWrapper;
import mil.tron.commonapi.service.PrivilegeService;

@RestController
@PreAuthorizeDashboardAdmin
public class PrivilegeController {
	
	private PrivilegeService privilegeService;
	
	public PrivilegeController(PrivilegeService privilegeService) {
		this.privilegeService = privilegeService;
	}
	
	/**
	 * @deprecated No longer valid T166. See {@link #getPrivilegesWrapped()} for new usage.
	 * @return
	 */
	@Operation(summary = "Retrieves all Privilege information", description = "Retrieves Privilege information")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
				description = "Successful operation", 
				content = @Content(array = @ArraySchema(schema = @Schema(implementation = PrivilegeDto.class))))
	})
	@Deprecated(since = "v2")
	@GetMapping({"${api-prefix.v1}/privilege"})
	public ResponseEntity<Object> getPrivileges() {
		return new ResponseEntity<>(privilegeService.getPrivileges(), HttpStatus.OK);
	}
	
	@Operation(summary = "Retrieves all Privilege information", description = "Retrieves Privilege information")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
				description = "Successful operation", 
				content = @Content(schema = @Schema(implementation = PrivilegeDtoResponseWrapper.class)))
	})
	@WrappedEnvelopeResponse
	@GetMapping({"${api-prefix.v2}/privilege"})
	public ResponseEntity<Object> getPrivilegesWrapped() {
		return new ResponseEntity<>(privilegeService.getPrivileges(), HttpStatus.OK);
	}
}
