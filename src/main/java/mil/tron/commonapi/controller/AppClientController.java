package mil.tron.commonapi.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.annotation.response.WrappedEnvelopeResponse;
import mil.tron.commonapi.annotation.security.PreAuthorizeDashboardAdmin;
import mil.tron.commonapi.dto.appclient.AppClientUserDetailsDto;
import mil.tron.commonapi.dto.appclient.AppClientUserDto;
import mil.tron.commonapi.dto.appclient.AppClientUserDtoResponseWrapped;
import mil.tron.commonapi.dto.PrivilegeDto;
import mil.tron.commonapi.dto.PrivilegeDtoResponseWrapper;
import mil.tron.commonapi.exception.ExceptionResponse;
import mil.tron.commonapi.exception.InvalidAppSourcePermissions;
import mil.tron.commonapi.service.AppClientUserService;
import mil.tron.commonapi.service.PrivilegeService;
import org.assertj.core.util.Lists;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
public class AppClientController {
	
	private AppClientUserService appClientService;
	private PrivilegeService privilegeService;

	private static final String DASHBOARD_ADMIN = "DASHBOARD_ADMIN";
	private static final String APP_CLIENT_DEVELOPER_PRIV = "APP_CLIENT_DEVELOPER";
	private static final String INVALID_PERMS = "Invalid User Permissions";
	

	public AppClientController(AppClientUserService appClientService, PrivilegeService privilegeService) {
		this.appClientService = appClientService;
		this.privilegeService = privilegeService;
	}

	private boolean getUserIsDashBoardAdmin() {
		return !SecurityContextHolder.getContext().getAuthentication()
				.getAuthorities()
				.stream()
				.filter(item -> item.getAuthority().equals(DASHBOARD_ADMIN))
				.collect(Collectors.toList())
				.isEmpty();
	}

	private void checkUserIsDashBoardAdminOrAppClientDeveloper(UUID appId) {
		if (!getUserIsDashBoardAdmin()) {
			// user wasn't a dashboard admin, so see if they're a APP_CLIENT_DEVELOPER for given appId...
			validateAppClientDeveloperAccessForUser(appId);
		}
	}

	/**
	 * Private helper to authorize a user to an app client's info identified by the app client's UUID
	 * @param appId the appid of the app client
	 */
	private void validateAppClientDeveloperAccessForUser(UUID appId) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		String userEmail = authentication.getCredentials().toString();  // get the JWT email string
		if (!appClientService.userIsAppClientDeveloperForApp(appId, userEmail)) {
			throw new InvalidAppSourcePermissions(INVALID_PERMS);
		}
	}

	/**
	 * Wrapper for the checkUserIsDashBoardAdminOrAppClientDeveloper but traps
	 * its exceptions and just returns false
	 * @param appId UUID of the app source
	 * @return true if user is Dashboard Admin or App Client Developer for given appId
	 */
	private boolean userIsDashBoardAdminOrAppClientDeveloper(UUID appId) {

		boolean result = true;

		try {
			this.checkUserIsDashBoardAdminOrAppClientDeveloper(appId);
		}
		catch (InvalidAppSourcePermissions ex) {
			result = false;
		}

		return result;
	}
	
	/**
	 * @deprecated No longer valid T166. See {@link #getAppClientUsersWrapped()} for new usage.
	 * @return
	 */
	@Operation(summary = "Retrieves all application client user information",
			description = "Retrieves application client user information.  Requires Dashboard Admin access or App Client Developer.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
				description = "Successful operation", 
				content = @Content(array = @ArraySchema(schema = @Schema(implementation = AppClientUserDto.class))))
	})
	@Deprecated(since = "v2")
	@GetMapping({"${api-prefix.v1}/app-client"})
	public ResponseEntity<Object> getAppClientUsers() {
		List<AppClientUserDto> dtos = Lists.newArrayList(this.appClientService
				.getAppClientUsers())
				.stream()
				.filter(source -> userIsDashBoardAdminOrAppClientDeveloper(source.getId()))
				.collect(Collectors.toList());

		return new ResponseEntity<>(dtos, HttpStatus.OK);
	}
	
	@Operation(summary = "Retrieves all application client user information",
			description = "Retrieves application client user information.  Requires Dashboard Admin access or App Client Developer.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
				description = "Successful operation", 
				content = @Content(schema = @Schema(implementation = AppClientUserDtoResponseWrapped.class)))
	})
	@WrappedEnvelopeResponse
	@GetMapping({"${api-prefix.v2}/app-client"})
	public ResponseEntity<Object> getAppClientUsersWrapped() {
		List<AppClientUserDto> dtos = Lists.newArrayList(this.appClientService
				.getAppClientUsers())
				.stream()
				.filter(source -> userIsDashBoardAdminOrAppClientDeveloper(source.getId()))
				.collect(Collectors.toList());

		return new ResponseEntity<>(dtos, HttpStatus.OK);
	}

	@Operation(summary = "Get an App Client's Information",
			description = "Get an App Client by its UUID. Requires DASHBOARD_ADMIN or be an App Client Developer of that UUID.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = AppClientUserDetailsDto.class))),
			@ApiResponse(responseCode = "403",
					description = "Requester isn't a DASHBOARD_ADMIN or an App Client Developer of this App Client",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "404",
					description = "Resource with that ID doesn't exist",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "400",
					description = "Bad request",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@GetMapping({"${api-prefix.v1}/app-client/{id}", "${api-prefix.v2}/app-client/{id}"})
	public ResponseEntity<Object> getAppClientRecord(@PathVariable UUID id) {

		checkUserIsDashBoardAdminOrAppClientDeveloper(id);
		return new ResponseEntity<>(appClientService.getAppClient(id), HttpStatus.OK);
	}
	
	@Operation(summary = "Adds an App Client",
			description = "Adds a App Client User. Requires DASHBOARD_ADMIN access.")
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
	@PreAuthorizeDashboardAdmin
	@PostMapping({"${api-prefix.v1}/app-client", "${api-prefix.v2}/app-client"})
	public ResponseEntity<AppClientUserDto> createAppClientUser(
			@Parameter(description = "App Client to create", required = true) @Valid @RequestBody AppClientUserDto appClient) {

		return new ResponseEntity<>(appClientService.createAppClientUser(appClient), HttpStatus.CREATED);
	}
	
	
	@Operation(summary = "Updates an existing Application Client",
			description = "Updates an existing Application Client. Requires DASHBOARD_ADMIN access to change any attribute," +
					"or be APP_CLIENT_DEVELOPER role for app client of given UUID to be able to manage change App Client Developers - " +
					"any of fields changed as APP_CLIENT_DEVELOPER will not be changed.")
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
	@PutMapping({"${api-prefix.v1}/app-client/{id}", "${api-prefix.v2}/app-client/{id}"})
	public ResponseEntity<AppClientUserDto> updateAppClient(
			@Parameter(description = "App Client ID to update", required = true) @PathVariable("id") UUID appClientId,
			@Parameter(description = "Updated app client record", required = true) @Valid @RequestBody AppClientUserDto appClient) {

		checkUserIsDashBoardAdminOrAppClientDeveloper(appClientId);

		if (getUserIsDashBoardAdmin()) {
			return new ResponseEntity<>(appClientService
					.updateAppClientUser(appClientId, appClient), HttpStatus.OK);
		}
		else {
			// they must be APP_CLIENT_DEVELOPER of this app then... so just let them modify certain fields
			return new ResponseEntity<>(appClientService
					.updateAppClientDeveloperItems(appClientId, appClient), HttpStatus.OK);
		}

	}
	
	
	@Operation(summary = "Deletes an App Client",
			description = "Deletes an existing App Client. Requires DASHBOARD_ADMIN access.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
            		content = @Content(schema = @Schema(implementation = AppClientUserDto.class))),
            @ApiResponse(responseCode = "404",
                    description = "Resource not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
	@PreAuthorizeDashboardAdmin
    @DeleteMapping({"${api-prefix.v1}/app-client/{id}", "${api-prefix.v2}/app-client/{id}"})
    public ResponseEntity<Object> deleteAppClient(
            @Parameter(description = "App Client ID to delete", required = true) @PathVariable("id") UUID id) {

        return new ResponseEntity<>(appClientService.deleteAppClientUser(id), HttpStatus.OK);
    }

	/**
	 * @deprecated No longer valid T166. See {@link #getClientTypePrivsWrapped()} for new usage.
	 * @return
	 */
	@Operation(summary = "Gets all available privileges available for an app-client",
			description = "Gets all the app client privileges so that privilege names can be mapped to their IDs. " +
							"Must be a DASHBOARD_ADMIN or APP_CLIENT_DEVELOPER")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Operation Successful",
					content = @Content(array = @ArraySchema(schema = @Schema(implementation = PrivilegeDto.class))))
	})
	@Deprecated(since = "v2")
	@GetMapping({"${api-prefix.v1}/app-client/privs"})
	@PreAuthorize("hasAuthority('DASHBOARD_ADMIN') || hasAuthority('APP_CLIENT_DEVELOPER')")
	public ResponseEntity<Object> getClientTypePrivs() {
		List<PrivilegeDto> scratchPrivs = Lists.newArrayList(privilegeService.getPrivileges())
				.stream()
				.filter(item -> item.getName().startsWith(APP_CLIENT_DEVELOPER_PRIV)
						|| item.getName().startsWith("WRITE")
						|| item.getName().startsWith("READ"))
				.collect(Collectors.toList());

		return new ResponseEntity<>(scratchPrivs, HttpStatus.OK);
	}
	
	@Operation(summary = "Gets all available privileges available for an app-client",
			description = "Gets all the app client privileges so that privilege names can be mapped to their IDs. " +
							"Must be a DASHBOARD_ADMIN or APP_CLIENT_DEVELOPER")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Operation Successful",
					content = @Content(schema = @Schema(implementation = PrivilegeDtoResponseWrapper.class)))
	})
	@WrappedEnvelopeResponse
	@GetMapping({"${api-prefix.v2}/app-client/privs"})
	@PreAuthorize("hasAuthority('DASHBOARD_ADMIN') || hasAuthority('APP_CLIENT_DEVELOPER')")
	public ResponseEntity<Object> getClientTypePrivsWrapped() {
		List<PrivilegeDto> scratchPrivs = Lists.newArrayList(privilegeService.getPrivileges())
				.stream()
				.filter(item -> item.getName().startsWith(APP_CLIENT_DEVELOPER_PRIV)
						|| item.getName().startsWith("WRITE")
						|| item.getName().startsWith("READ"))
				.collect(Collectors.toList());

		return new ResponseEntity<>(scratchPrivs, HttpStatus.OK);
	}
}
