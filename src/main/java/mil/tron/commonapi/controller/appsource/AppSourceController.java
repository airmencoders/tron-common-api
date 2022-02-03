package mil.tron.commonapi.controller.appsource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.annotation.response.WrappedEnvelopeResponse;
import mil.tron.commonapi.annotation.security.PreAuthorizeDashboardAdmin;
import mil.tron.commonapi.dto.DashboardUserDto;
import mil.tron.commonapi.dto.appclient.AppClientSummaryDto;
import mil.tron.commonapi.dto.appclient.AppClientSummaryDtoResponseWrapper;
import mil.tron.commonapi.dto.appsource.AppEndPointPrivDto;
import mil.tron.commonapi.dto.appsource.AppSourceDetailsDto;
import mil.tron.commonapi.dto.appsource.AppSourceDto;
import mil.tron.commonapi.dto.appsource.AppSourceDtoResponseWrapper;
import mil.tron.commonapi.exception.ExceptionResponse;
import mil.tron.commonapi.exception.InvalidAppSourcePermissions;
import mil.tron.commonapi.service.AppClientUserService;
import mil.tron.commonapi.service.AppSourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static mil.tron.commonapi.service.DashboardUserServiceImpl.DASHBOARD_ADMIN_PRIV;

/***
 * Controller for App Source endpoints.
 */
@RestController
public class AppSourceController {

    private AppClientUserService appClientUserService;
    private AppSourceService appSourceService;
    private static final String INVALID_PERMS = "Invalid User Permissions";

    @Autowired
    AppSourceController(AppSourceService appSourceService, AppClientUserService appClientUserService) {
        this.appSourceService = appSourceService;
        this.appClientUserService = appClientUserService;
    }

    private void checkUserIsDashBoardAdminOrAppSourceAdmin(UUID appId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        List<GrantedAuthority> auths = authentication
                .getAuthorities()
                .stream()
                .filter(item -> item.getAuthority().equals(DASHBOARD_ADMIN_PRIV))
                .collect(Collectors.toList());

        if (auths.isEmpty()) {
            // user wasn't a dashboard admin, so see if they're a APP_SOURCE_ADMIN for given appId...
            validateAppSourceAdminAccessForUser(appId);
        }
    }

    /**
     * Private helper to authorize a user to a scratch space identified by the app UUID for admin access
     * @param appId the appid of the app's data user/request is trying to access
     */
    private void validateAppSourceAdminAccessForUser(UUID appId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = authentication.getCredentials().toString();  // get the JWT email string
        if (!appSourceService.userIsAdminForAppSource(appId, userEmail)) {
            throw new InvalidAppSourcePermissions(INVALID_PERMS);
        }
    }

    /**
     * Wrapper for the checkUserIsDashBoardAdminOrAppSourceAdmin but traps
     * its exceptions and just returns false
     * @param appId UUID of the app source
     * @return true if user is Dashboard Admin or App Source admin for given appId
     */
    private boolean userIsDashBoardAdminOrAppSourceAdmin(UUID appId) {

        boolean result = true;

        try {
            this.checkUserIsDashBoardAdminOrAppSourceAdmin(appId);
        }
        catch (InvalidAppSourcePermissions ex) {
            result = false;
        }

        return result;
    }

    @Operation(summary = "Creates an App Source including App Client permissions.", description = "Requires DASHBOARD_ADMIN rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201",
                    description = "Successful creation"),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges",
            		content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExceptionResponse.class)
					)),
            @ApiResponse(responseCode = "400",
                    description = "Malformed Request Body",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExceptionResponse.class)
                    ))
    })
    @PreAuthorizeDashboardAdmin
    @PostMapping({"${api-prefix.v1}/app-source", "${api-prefix.v2}/app-source"})
    public ResponseEntity<AppSourceDetailsDto> createAppSource(
            @Parameter(name = "App Source", required = true) @Valid @RequestBody AppSourceDetailsDto appSourceDto) {
        return new ResponseEntity<>(this.appSourceService.createAppSource(appSourceDto), HttpStatus.CREATED);
    }

    /**
     * @deprecated No longer valid T166. See {@link #getAppSourcesWrapped()} for new usage.
     * @return
     */
    @Operation(summary = "Gets all App Sources.", description = "Requires DASHBOARD_ADMIN rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = AppSourceDto.class)))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges",
            		content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExceptionResponse.class)
					))
    })
    @Deprecated(since = "v2")
    @GetMapping({"${api-prefix.v1}/app-source"})
    public ResponseEntity<List<AppSourceDto>> getAppSources() {
        List<AppSourceDto> dtos = this.appSourceService
                .getAppSources()
                .stream()
                .filter(source -> userIsDashBoardAdminOrAppSourceAdmin(source.getId()))
                .collect(Collectors.toList());

        return new ResponseEntity<>(dtos, HttpStatus.OK);
    }
    
    @Operation(summary = "Gets all App Sources.", description = "Requires DASHBOARD_ADMIN rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AppSourceDtoResponseWrapper.class))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges",
            		content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExceptionResponse.class)
					))
    })
    @WrappedEnvelopeResponse
    @GetMapping({"${api-prefix.v2}/app-source"})
    public ResponseEntity<List<AppSourceDto>> getAppSourcesWrapped() {
        List<AppSourceDto> dtos = this.appSourceService
                .getAppSources()
                .stream()
                .filter(source -> userIsDashBoardAdminOrAppSourceAdmin(source.getId()))
                .collect(Collectors.toList());

        return new ResponseEntity<>(dtos, HttpStatus.OK);
    }

    @Operation(summary = "Returns the details for an App Source", description = "Requires DASHBOARD_ADMIN or APP_SOURCE_ADMIN rights.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AppSourceDetailsDto.class))),
            @ApiResponse(responseCode = "403",
                    description = "Insufficient privileges",
            		content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExceptionResponse.class)
					)),
            @ApiResponse(responseCode = "404",
                    description = "Requested App Source not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExceptionResponse.class)
                    ))
    })
    @GetMapping({"${api-prefix.v1}/app-source/{id}", "${api-prefix.v2}/app-source/{id}"})
    public ResponseEntity<AppSourceDetailsDto> getAppSourceDetails(
            @Parameter(name = "id", description = "App Source UUID", required = true) @PathVariable UUID id) {
        checkUserIsDashBoardAdminOrAppSourceAdmin(id);
        return new ResponseEntity<>(this.appSourceService.getAppSource(id), HttpStatus.OK);
    }

    @Operation(summary = "Updates the details for an App Source", description = "Requires DASHBOARD_ADMIN rights or be APP_SOURCE_ADMIN "
        + " of given App Id. Admin users can also be managed via this request method.  Emails for app source admins to a given App Source UUID will be implicitly added as new "
        + " DashboardUsers with the APP_SOURCE_ADMIN privilege.  Conversely a PUT that takes away an email that was there before "
        + " will be deleted as a DashboardUser if that email address does not have any other privileges in the system or its an "
        + " app source admin to some other app source application.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AppSourceDetailsDto.class))),
            @ApiResponse(responseCode = "404",
                    description = "Requested App Source not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExceptionResponse.class)
                    )),
            @ApiResponse(responseCode = "403",
                    description = "Insufficient privileges",
            		content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExceptionResponse.class)
					)),
            @ApiResponse(responseCode = "400",
                    description = "Malformed Request Body",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExceptionResponse.class)
                    ))
    })
    @PutMapping({"${api-prefix.v1}/app-source/{id}", "${api-prefix.v2}/app-source/{id}"})
    public ResponseEntity<AppSourceDetailsDto> updateAppSourceDetails(
            @Parameter(name = "id", description = "App Source id to update", required = true)
                    @PathVariable UUID id,
            @Parameter(description = "App Source Dto", required = true)
            @Valid @RequestBody AppSourceDetailsDto appSourceDetailsDto) {

        checkUserIsDashBoardAdminOrAppSourceAdmin(id);
        return new ResponseEntity<>(this.appSourceService.updateAppSource(id, appSourceDetailsDto), HttpStatus.OK);
    }

    @Operation(summary = "Deletes the App Source",
            description = "Requester has to have DASHBOARD_ADMIN rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "App Source Removed OK",
                    content = @Content(schema = @Schema(implementation = AppSourceDetailsDto.class))),
            @ApiResponse(responseCode = "400",
                    description = "Id is malformed",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "App Source Id not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges",
            		content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExceptionResponse.class)
					))
    })
    @PreAuthorizeDashboardAdmin
    @DeleteMapping({"${api-prefix.v1}/app-source/{id}", "${api-prefix.v2}/app-source/{id}"})
    public ResponseEntity<Object> deleteAppSource(
            @Parameter(name = "id", description = "App Source UUID", required = true) @PathVariable UUID id) {

        return new ResponseEntity<>(appSourceService.deleteAppSource(id), HttpStatus.OK);
    }

    // app source user management, patch endpoints for adding removing app source admins, singularly.
    //   Multiple users can be done in a whole-record PUT request using endpoints above...

    @Operation(summary = "Adds single app source admin by email address to provided App Source",
            description = "Requester has to have DASHBOARD_ADMIN rights or be APP_SOURCE_ADMIN of given App Id.  Request payload is a DashboardUserDto, " +
                    "but only needed/required fields are the email address.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Admin Added OK",
                    content = @Content(schema = @Schema(implementation = AppSourceDetailsDto.class))),
            @ApiResponse(responseCode = "400",
                    description = "Id is malformed",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "App Source Id not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
                    description = "Insufficient privileges",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExceptionResponse.class)
                    ))
    })
    @PatchMapping({"${api-prefix.v1}/app-source/admins/{id}", "${api-prefix.v2}/app-source/admins/{id}"})
    public ResponseEntity<Object> addAppSourceAdmin(
            @Parameter(name = "id", description = "App Source UUID", required = true) @PathVariable UUID id,
            @Parameter(name = "Email", description = "Email of user to add as an App Source admin", required = true) @Valid @RequestBody DashboardUserDto user) {

        checkUserIsDashBoardAdminOrAppSourceAdmin(id);
        return new ResponseEntity<>(appSourceService.addAppSourceAdmin(id, user.getEmail()), HttpStatus.OK);
    }

    @Operation(summary = "Deletes a single app source admin by email address from provided App Source",
            description = "Requester has to have DASHBOARD_ADMIN rights or be APP_SOURCE_ADMIN of given App Id.  Request payload is a DashboardUserDto, " +
                    "but only needed/required fields are the email address.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Admin Removed OK",
                    content = @Content(schema = @Schema(implementation = AppSourceDetailsDto.class))),
            @ApiResponse(responseCode = "400",
                    description = "Id is malformed",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "App Source Id not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
                    description = "Insufficient privileges",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExceptionResponse.class)
                    ))
    })
    @DeleteMapping({"${api-prefix.v1}/app-source/admins/{id}", "${api-prefix.v2}/app-source/admins/{id}"})
    public ResponseEntity<Object> removeAppSourceAdmin(
            @Parameter(name = "id", description = "App Source UUID", required = true) @PathVariable UUID id,
            @Parameter(name = "Email", description = "Admin To Remove Email", required = true) @Valid @RequestBody DashboardUserDto user) {

        checkUserIsDashBoardAdminOrAppSourceAdmin(id);
        return new ResponseEntity<>(appSourceService.removeAdminFromAppSource(id, user.getEmail()), HttpStatus.OK);
    }

    // app-source endpoint management endpoints, manages client app access to this app-sources endpoints

    @Operation(summary = "Deletes ALL app client privileges from provided App Source.  No App Clients will be able to use this app source's endpoints.",
            description = "Requester has to have DASHBOARD_ADMIN rights or be APP_SOURCE_ADMIN of given App Id.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "All App Client Privileges Removed OK",
                    content = @Content(schema = @Schema(implementation = AppSourceDetailsDto.class))),
            @ApiResponse(responseCode = "400",
                    description = "Id is malformed",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "App Source not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
                    description = "Insufficient privileges",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExceptionResponse.class)
                    ))
    })
    @DeleteMapping({"${api-prefix.v1}/app-source/app-clients/all/{id}", "${api-prefix.v2}/app-source/app-clients/all/{id}"})
    public ResponseEntity<Object> removeAllAppClientPrivs(
            @Parameter(name = "id", description = "App Source UUID", required = true) @PathVariable UUID id) {
        checkUserIsDashBoardAdminOrAppSourceAdmin(id);
        return new ResponseEntity<>(appSourceService.deleteAllAppClientPrivs(id), HttpStatus.OK);
    }

    /**
     * @deprecated No longer valid T166. See {@link #getAvailableAppClientsWrapped()} for new usage.
     * @return
     */
    @Operation(summary = "Gets a list of the available app clients (their names and UUIDs)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
            		content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = AppClientSummaryDto.class)))),
            @ApiResponse(responseCode = "403",
            description = "Insufficient privileges",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ExceptionResponse.class)
            ))
    })
    @Deprecated(since = "v2")
    @GetMapping({"${api-prefix.v1}/app-source/app-clients"})
    @PreAuthorize("hasAuthority('DASHBOARD_ADMIN') or hasAuthority('APP_SOURCE_ADMIN')")
    public ResponseEntity<Object> getAvailableAppClients() {
        return new ResponseEntity<>(appClientUserService.getAppClientUserSummaries(), HttpStatus.OK);
    }
    
    @Operation(summary = "Gets a list of the available app clients (their names and UUIDs)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
            		content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AppClientSummaryDtoResponseWrapper.class))),
            @ApiResponse(responseCode = "403",
            description = "Insufficient privileges",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ExceptionResponse.class)
            ))
    })
    @WrappedEnvelopeResponse
    @GetMapping({"${api-prefix.v2}/app-source/app-clients"})
    @PreAuthorize("hasAuthority('DASHBOARD_ADMIN') or hasAuthority('APP_SOURCE_ADMIN')")
    public ResponseEntity<Object> getAvailableAppClientsWrapped() {
        return new ResponseEntity<>(appClientUserService.getAppClientUserSummaries(), HttpStatus.OK);
    }

    @Operation(summary = "Adds an app source's endpoint to app client privilege relationship",
            description = "Requester has to have DASHBOARD_ADMIN rights or be APP_SOURCE_ADMIN of given App Id.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Record Added/Updated OK",
                    content = @Content(schema = @Schema(implementation = AppSourceDetailsDto.class))),
            @ApiResponse(responseCode = "400",
                    description = "An Id is malformed",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "App Source/End Point/App Client not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
                    description = "Insufficient privileges",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExceptionResponse.class)
                    ))
    })
    @PostMapping({"${api-prefix.v1}/app-source/app-clients", "${api-prefix.v2}/app-source/app-clients"})
    public ResponseEntity<Object> addClientToEndpointPriv(
            @Parameter(name = "appId", description = "App Source UUID", required = true) @Valid @RequestBody AppEndPointPrivDto dto) {
        checkUserIsDashBoardAdminOrAppSourceAdmin(dto.getAppSourceId());
        return new ResponseEntity<>(appSourceService.addEndPointPrivilege(dto), HttpStatus.CREATED);
    }

    @Operation(summary = "Deletes an app source's endpoint to app client privilege relationship",
            description = "Requester has to have DASHBOARD_ADMIN rights or be APP_SOURCE_ADMIN of given App Id.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Privilege Removed OK",
                    content = @Content(schema = @Schema(implementation = AppSourceDetailsDto.class))),
            @ApiResponse(responseCode = "400",
                    description = "An Id is malformed",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "App Source/End Point/App Client not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
                    description = "Insufficient privileges",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExceptionResponse.class)
                    ))
    })
    @DeleteMapping({"${api-prefix.v1}/app-source/app-clients/{appId}/{privId}", "${api-prefix.v2}/app-source/app-clients/{appId}/{privId}"})
    public ResponseEntity<Object> removeClientToEndPointPriv(
            @Parameter(name = "appId", description = "App Source UUID", required = true) @PathVariable UUID appId,
            @Parameter(name = "privId", description = "App Source Endpoint Privilege UUID", required = true) @PathVariable UUID privId) {
        checkUserIsDashBoardAdminOrAppSourceAdmin(appId);
        return new ResponseEntity<>(appSourceService.removeEndPointPrivilege(appId, privId), HttpStatus.OK);
    }

    @Operation(summary = "Gets a copy of the openapispec file for the app source")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200",
                description = "Successful operation",
                content = @Content(
                        mediaType = "application/vnd.oai.openapi",
                        schema = @Schema(implementation = Resource.class)
                )),
        @ApiResponse(responseCode = "400",
                description = "Id is malformed",
                content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(responseCode = "403",
                description = "Insufficient privileges",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ExceptionResponse.class)
                )),
        @ApiResponse(responseCode = "404",
                description = "App Source or API Specification file not found",
                content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
    })
    @GetMapping({"${api-prefix.v1}/app-source/spec/{appId}", "${api-prefix.v2}/app-source/spec/{appId}"})
    @PostAuthorize("hasAuthority('DASHBOARD_ADMIN') || @accessCheckAppSource.checkByAppSourceId(authentication, #appId)")
    public ResponseEntity<Resource> getSpecFile(@Parameter(name = "appId", description = "App Source UUID", required = true) @PathVariable UUID appId) {
        return new ResponseEntity<>(appSourceService.getApiSpecForAppSource(appId), HttpStatus.OK);
    }

    @Operation(summary = "Gets a copy of the openapispec file for the app source related to the endpoint")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200",
                description = "Successful operation",
                content = @Content(
                        mediaType = "application/vnd.oai.openapi",
                        schema = @Schema(implementation = Resource.class)
                )),
        @ApiResponse(responseCode = "400",
                description = "Id is malformed",
                content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(responseCode = "403",
                description = "Insufficient privileges",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ExceptionResponse.class)
                )),
        @ApiResponse(responseCode = "404",
                description = "App Source or API Specification file not found",
                content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
    })
    @GetMapping({"${api-prefix.v1}/app-source/spec/endpoint-priv/{endpointPrivId}", "${api-prefix.v2}/app-source/spec/endpoint-priv/{endpointPrivId}"})
    @PostAuthorize("hasAuthority('DASHBOARD_ADMIN') || @accessCheckAppSource.checkByAppEndpointPrivId(authentication, #endpointPrivId)")
    public ResponseEntity<Resource> getSpecFileByEndpointPriv(@Parameter(name = "endpointPrivId", description = "App Endpoint Privilege UUID", required = true) @PathVariable UUID endpointPrivId) {
        return new ResponseEntity<>(appSourceService.getApiSpecForAppSourceByEndpointPriv(endpointPrivId), HttpStatus.OK);
    }
}
