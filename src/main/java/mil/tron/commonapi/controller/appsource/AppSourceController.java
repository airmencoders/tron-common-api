package mil.tron.commonapi.controller.appsource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.annotation.security.PreAuthorizeDashboardAdmin;
import mil.tron.commonapi.dto.DashboardUserDto;
import mil.tron.commonapi.dto.appsource.AppSourceDetailsDto;
import mil.tron.commonapi.dto.appsource.AppSourceDto;
import mil.tron.commonapi.exception.ExceptionResponse;
import mil.tron.commonapi.exception.InvalidAppSourcePermissions;
import mil.tron.commonapi.service.AppSourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/***
 * Controller for App Source endpoints.
 */
@RestController
@RequestMapping("${api-prefix.v1}/app-source")
public class AppSourceController {

    private AppSourceService appSourceService;
    private static final String DASHBOARD_ADMIN = "DASHBOARD_ADMIN";
    private static final String INVALID_PERMS = "Invalid User Permissions";

    @Autowired
    AppSourceController(AppSourceService appSourceService) {
        this.appSourceService = appSourceService;
    }

    private void checkUserIsDashBoardAdminOrAppSourceAdmin(UUID appId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        List<GrantedAuthority> auths = authentication
                .getAuthorities()
                .stream()
                .filter(item -> item.getAuthority().equals(DASHBOARD_ADMIN))
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
    @PostMapping
    public ResponseEntity<AppSourceDetailsDto> createAppSource(
            @Parameter(name = "App Source", required = true) @Valid @RequestBody AppSourceDetailsDto appSourceDto) {
        return new ResponseEntity<>(this.appSourceService.createAppSource(appSourceDto), HttpStatus.CREATED);
    }

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
    @PreAuthorizeDashboardAdmin
    @GetMapping
    public ResponseEntity<List<AppSourceDto>> getAppSources() {
        return new ResponseEntity<>(this.appSourceService.getAppSources(), HttpStatus.OK);
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
    @GetMapping("/{id}")
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
    @PutMapping("/{id}")
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
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteAppSource(
            @Parameter(name = "id", description = "App Source UUID", required = true) @PathVariable UUID id) {

        return new ResponseEntity<>(appSourceService.deleteAppSource(id), HttpStatus.OK);
    }

    // app source user management, patch endpoints for adding removing app source admins, singularly
    //   multiples can be done in a whole-record PUT request above...

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
    @PatchMapping("/admins/{id}")
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
    @DeleteMapping("/admins/{id}")
    public ResponseEntity<Object> removeAppSourceAdmin(
            @Parameter(name = "id", description = "App Source UUID", required = true) @PathVariable UUID id,
            @Parameter(name = "Email", description = "Admin To Remove Email", required = true) @Valid @RequestBody DashboardUserDto user) {

        checkUserIsDashBoardAdminOrAppSourceAdmin(id);
        return new ResponseEntity<>(appSourceService.removeAdminFromAppSource(id, user.getEmail()), HttpStatus.OK);
    }

}