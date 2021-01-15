package mil.tron.commonapi.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.dto.UserInfoDto;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.exception.ExceptionResponse;

@RestController
@RequestMapping("${api-prefix.v1}/userinfo")
public class UserInfoController {
	
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
		
		if (authHeader == null || authHeader.isBlank()) {
			throw new BadRequestException("Authorization header in request missing.");
		}
		
		String[] splitToken = authHeader.split("Bearer ");
		
		if (splitToken.length != 2) {
			throw new BadRequestException("Authorization header in request is malformed.");
		}
		
		String token = splitToken[1];
		DecodedJWT jwt = JWT.decode(token);
		
		UserInfoDto userInfo = new UserInfoDto();
		userInfo.setGivenName(jwt.getClaim("given_name").asString());
		userInfo.setFamilyName(jwt.getClaim("family_name").asString());
		userInfo.setName(jwt.getClaim("name").asString());
		userInfo.setPreferredUsername(jwt.getClaim("preferred_username").asString());
		userInfo.setEmail(jwt.getClaim("email").asString());
		userInfo.setOrganization(jwt.getClaim("organization").asString());
		
		return new ResponseEntity<>(userInfo, HttpStatus.OK);
	}
}
