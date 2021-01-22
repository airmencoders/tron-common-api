package mil.tron.commonapi.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.dto.UserInfoDto;
import mil.tron.commonapi.exception.ExceptionResponse;
import mil.tron.commonapi.service.UserInfoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("${api-prefix.v1}/userinfo")
public class UserInfoController {
	private UserInfoService userInfoService;
	
	public UserInfoController(UserInfoService userInfoService) {
		this.userInfoService = userInfoService;
	}
	
	@Operation(summary = "Retrieves the user information from the jwt", description = "Retrieves user information")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
				description = "Successful operation", 
				content = @Content(schema = @Schema(implementation = UserInfoDto.class))),
			@ApiResponse(responseCode = "400",
				description = "Bad request",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@GetMapping
	public ResponseEntity<UserInfoDto> getUserInfo(@RequestHeader Map<String, String> headers) {
		
		String authHeader = headers.get("authorization");
		
		UserInfoDto userInfo = userInfoService.extractUserInfoFromHeader(authHeader);
		
		return new ResponseEntity<>(userInfo, HttpStatus.OK);
	}
}
