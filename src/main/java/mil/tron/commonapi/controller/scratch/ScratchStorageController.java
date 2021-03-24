package mil.tron.commonapi.controller.scratch;

import com.google.common.collect.Lists;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.annotation.security.PreAuthorizeDashboardAdmin;
import mil.tron.commonapi.dto.PrivilegeDto;
import mil.tron.commonapi.dto.ScratchStorageAppRegistryDto;
import mil.tron.commonapi.dto.ScratchStorageAppUserPrivDto;
import mil.tron.commonapi.entity.scratch.ScratchStorageAppRegistryEntry;
import mil.tron.commonapi.entity.scratch.ScratchStorageEntry;
import mil.tron.commonapi.entity.scratch.ScratchStorageUser;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.exception.InvalidScratchSpacePermissions;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.service.PrivilegeService;
import mil.tron.commonapi.service.scratch.ScratchStorageService;
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

@RestController
@RequestMapping("${api-prefix.v1}/scratch")
public class ScratchStorageController {
    private ScratchStorageService scratchStorageService;
    private PrivilegeService privilegeService;
    private static final String DASHBOARD_ADMIN = "DASHBOARD_ADMIN";
    private static final String INVALID_PERMS = "Invalid User Permissions";

    public ScratchStorageController(ScratchStorageService scratchStorageService, PrivilegeService privilegeService) {
        this.scratchStorageService = scratchStorageService;
        this.privilegeService = privilegeService;
    }

    private void checkUserIsDashBoardAdminOrScratchAdmin(UUID appId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        List<GrantedAuthority> auths = authentication
                .getAuthorities()
                .stream()
                .filter(item -> item.getAuthority().equals(DASHBOARD_ADMIN))
                .collect(Collectors.toList());

        if (auths.isEmpty()) {
            // user wasn't a dashboard admin, so see if they're a SCRATCH_ADMIN for given appId...
            validateScratchAdminAccessForUser(appId);
        }
    }

    /**
     * Private helper to authorize a user to a scratch space identified by the app UUID for write access
     * @param appId the appid of the app's data user/request is trying to access
     */
    private void validateScratchWriteAccessForUser(UUID appId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = authentication.getCredentials().toString();  // get the JWT email string
        if (!scratchStorageService.userCanWriteToAppId(appId, userEmail)) {
            throw new InvalidScratchSpacePermissions(INVALID_PERMS);
        }
    }

    /**
     * Private helper to authorize a user to a scratch space identified by the app UUID for admin access
     * @param appId the appid of the app's data user/request is trying to access
     */
    private void validateScratchAdminAccessForUser(UUID appId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = authentication.getCredentials().toString();  // get the JWT email string
        if (!scratchStorageService.userHasAdminWithAppId(appId, userEmail)) {
            throw new InvalidScratchSpacePermissions(INVALID_PERMS);
        }
    }

    @Operation(summary = "Retrieves all key-value pairs for all scratch space consuming apps",
            description = "Requires request to be under DASHBOARD_ADMIN privileges")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScratchStorageEntry.class)))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges")
    })
    @PreAuthorizeDashboardAdmin  // only admin can see everyone's key-value pairs
    @GetMapping("")
    public ResponseEntity<Object> getAllKeyValuePairs() {
        return new ResponseEntity<>(scratchStorageService.getAllEntries(), HttpStatus.OK);
    }

    @Operation(summary = "Retrieves all key-value pairs for for a single app",
            description = "App ID is the UUID of the owning application")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScratchStorageEntry.class)))),
            @ApiResponse(responseCode = "404",
                    description = "Application ID not valid or found",
                    content = @Content(schema = @Schema(implementation = RecordNotFoundException.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed Application UUID")
    })
    @GetMapping("/{appId}")
    public ResponseEntity<Object> getAllKeyValuePairsForAppId(
            @Parameter(name = "appId", description = "Application UUID", required = true) @PathVariable UUID appId) {
        return new ResponseEntity<>(scratchStorageService.getAllEntriesByApp(appId), HttpStatus.OK);
    }

    @Operation(summary = "Retrieves all keys for for a single app",
            description = "App ID is the UUID of the owning application")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScratchStorageEntry.class)))),
            @ApiResponse(responseCode = "404",
                    description = "Application ID not valid or found",
                    content = @Content(schema = @Schema(implementation = RecordNotFoundException.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed Application UUID")
    })
    @GetMapping("/apps/{appId}/keys")
    public ResponseEntity<Object> getAllKeysForAppId(
            @Parameter(name = "appId", description = "Application UUID", required = true) @PathVariable UUID appId) {
        return new ResponseEntity<>(scratchStorageService.getAllKeysForAppId(appId), HttpStatus.OK);
    }

    @Operation(summary = "Retrieves a singe key-value pair for for a single app",
            description = "App ID is the UUID of the owning application")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = ScratchStorageEntry.class))),
            @ApiResponse(responseCode = "404",
                    description = "Application ID / Key name not valid or found",
                    content = @Content(schema = @Schema(implementation = RecordNotFoundException.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed Application UUID")
    })
    @GetMapping("/{appId}/{keyName}")
    public ResponseEntity<Object> getKeyValueByKeyName(
            @Parameter(name = "appId", description = "Application UUID", required = true) @PathVariable UUID appId,
            @Parameter(name = "keyName", description = "Key Name to look up", required = true) @PathVariable String keyName) {
        return new ResponseEntity<>(scratchStorageService.getKeyValueEntryByAppId(appId, keyName), HttpStatus.OK);
    }

    @Operation(summary = "Adds or updates a key-value pair for a given App Id",
            description = "SCRATCH_WRITE privileges are required for the requester for the given App Id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = ScratchStorageEntry.class))),
            @ApiResponse(responseCode = "403",
                    description = "Write / Update action forbidden - no WRITE privileges",
                    content = @Content(schema = @Schema(implementation = InvalidScratchSpacePermissions.class))),
            @ApiResponse(responseCode = "404",
                    description = "Application ID / Key name not valid or found",
                    content = @Content(schema = @Schema(implementation = RecordNotFoundException.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed Request Body")
    })
    @PostMapping("")
    public ResponseEntity<Object> setKeyValuePair(
            @Parameter(name = "entry", description = "Key-Value-AppId object", required = true) @Valid @RequestBody ScratchStorageEntry entry) {

        validateScratchWriteAccessForUser(entry.getAppId());

        return new ResponseEntity<>(
                scratchStorageService.setKeyValuePair(entry.getAppId(), entry.getKey(), entry.getValue()), HttpStatus.OK);
    }

    @Operation(summary = "Deletes a key-value pair for a given App Id",
            description = "SCRATCH_WRITE privileges are required for the requester for the given App Id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = ScratchStorageEntry.class))),
            @ApiResponse(responseCode = "403",
                    description = "Write / Update action forbidden - no WRITE privileges",
                    content = @Content(schema = @Schema(implementation = InvalidScratchSpacePermissions.class))),
            @ApiResponse(responseCode = "404",
                    description = "Application ID / Key name not valid or found",
                    content = @Content(schema = @Schema(implementation = RecordNotFoundException.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed Request Body")
    })
    @DeleteMapping("/{appId}/{key}")
    public ResponseEntity<Object> deleteKeyValuePair(
            @Parameter(name = "appId", description = "Application UUID", required = true) @PathVariable UUID appId,
            @Parameter(name = "key", description = "Key name of the key-value pair to delete", required = true) @PathVariable String key) {
        validateScratchWriteAccessForUser(appId);

        return new ResponseEntity<>(scratchStorageService.deleteKeyValuePair(appId, key), HttpStatus.OK);
    }

    @Operation(summary = "Deletes all key-value pairs for a given App Id",
            description = "SCRATCH_WRITE privileges are required for the requester for the given App Id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = ScratchStorageEntry.class))),
            @ApiResponse(responseCode = "403",
                    description = "Write / Update action forbidden - no WRITE privileges",
                    content = @Content(schema = @Schema(implementation = InvalidScratchSpacePermissions.class))),
            @ApiResponse(responseCode = "404",
                    description = "Application ID not valid or found",
                    content = @Content(schema = @Schema(implementation = RecordNotFoundException.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed Request Body")
    })
    @DeleteMapping("/{appId}")
    public ResponseEntity<Object> deleteAllKeyValuePairsForAppId(
            @Parameter(name = "appId", description = "Application UUID", required = true) @PathVariable UUID appId) {
        validateScratchWriteAccessForUser(appId);

        return new ResponseEntity<>(scratchStorageService.deleteAllKeyValuePairsForAppId(appId), HttpStatus.OK);
    }

    // scratch app registration/management endpoints... only admins can manage these

    @Operation(summary = "Gets the entire table of Scratch Storage apps that are registered with Common API",
            description = "Requester has to have DASHBOARD_ADMIN rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScratchStorageAppRegistryDto.class)))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges")
    })
    @PreAuthorizeDashboardAdmin
    @GetMapping("/apps")
    public ResponseEntity<Object> getScratchSpaceApps() {
        return new ResponseEntity<>(scratchStorageService.getAllRegisteredScratchApps(), HttpStatus.OK);
    }

    @Operation(summary = "Gets a single Scratch Storage app's record that is registered with Common API",
            description = "Requester has to have DASHBOARD_ADMIN rights or have SCRATCH_ADMIN rights for given app ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = ScratchStorageAppRegistryDto.class))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges, or no SCRATCH_ADMIN privilege for given app ID.")
    })
    @GetMapping("/apps/{appId}")
    public ResponseEntity<Object> getScratchAppById(@PathVariable UUID appId) {

        checkUserIsDashBoardAdminOrScratchAdmin(appId);
        return new ResponseEntity<>(scratchStorageService.getRegisteredScratchApp(appId), HttpStatus.OK);
    }

    @Operation(summary = "Adds a new Scratch Strorage consuming app name to the Common API scratch storage space",
            description = "Requester has to have DASHBOARD_ADMIN rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201",
                    description = "App Registered OK",
                    content = @Content(schema = @Schema(implementation = ScratchStorageAppRegistryEntry.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed request body or app name already exists",
                    content = @Content(schema = @Schema(implementation = BadRequestException.class))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges"),
            @ApiResponse(responseCode = "409",
                    description = "App UUID or App Name is already is use",
                    content = @Content(schema = @Schema(implementation = ResourceAlreadyExistsException.class)))
    })
    @PreAuthorizeDashboardAdmin
    @PostMapping("/apps")
    public ResponseEntity<Object> postNewScratchSpaceApp(
            @Parameter(name = "entry", description = "New Application Information", required = true) @Valid @RequestBody ScratchStorageAppRegistryEntry entry) {
        return new ResponseEntity<>(scratchStorageService.addNewScratchAppName(entry), HttpStatus.CREATED);
    }

    @Operation(summary = "Edit existing scratch space app information (its name)",
            description = "Requester has to have DASHBOARD_ADMIN rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "App Info Changed OK",
                    content = @Content(schema = @Schema(implementation = ScratchStorageAppRegistryEntry.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed request body / app name already exists or appId is malformed",
                    content = @Content(schema = @Schema(implementation = BadRequestException.class))),
            @ApiResponse(responseCode = "404",
                    description = "Application ID not found",
                    content = @Content(schema = @Schema(implementation = RecordNotFoundException.class))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges"),
            @ApiResponse(responseCode = "409",
                    description = "App Name is already is use",
                    content = @Content(schema = @Schema(implementation = ResourceAlreadyExistsException.class)))
    })
    @PreAuthorizeDashboardAdmin
    @PutMapping("/apps/{id}")
    public ResponseEntity<Object> editExistingAppEntry(
            @Parameter(name = "id", description = "Application UUID", required = true) @PathVariable UUID id,
            @Parameter(name = "entry", description = "Application Information Object", required = true) @Valid @RequestBody ScratchStorageAppRegistryEntry entry) {
        return new ResponseEntity<>(scratchStorageService.editExistingScratchAppEntry(id, entry), HttpStatus.OK);
    }

    @Operation(summary = "Adds a user privilege to this app's data",
            description = "Requester has to have DASHBOARD_ADMIN rights, or have SCRATCH_ADMIN rights for given app ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "App Priv Added OK",
                    content = @Content(schema = @Schema(implementation = ScratchStorageAppRegistryEntry.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed request body / app name already exists or appId is malformed",
                    content = @Content(schema = @Schema(implementation = BadRequestException.class))),
            @ApiResponse(responseCode = "404",
                    description = "Application ID not found",
                    content = @Content(schema = @Schema(implementation = RecordNotFoundException.class))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges, or no SCRATCH_ADMIN privileges for given app id"),
            @ApiResponse(responseCode = "409",
                    description = "This app/user/priv combo already exists",
                    content = @Content(schema = @Schema(implementation = ResourceAlreadyExistsException.class)))
    })
    @PatchMapping("/apps/{id}/user")
    public ResponseEntity<Object> addUserPriv(
            @Parameter(name = "id", description = "Application UUID", required = true) @PathVariable UUID id,
            @Parameter(name = "priv", description = "Application User-Priv Object", required = true) @Valid @RequestBody ScratchStorageAppUserPrivDto priv) {

        checkUserIsDashBoardAdminOrScratchAdmin(id);
        ScratchStorageAppRegistryEntry p = scratchStorageService.addUserPrivToApp(id, priv);
        return new ResponseEntity<>(p, HttpStatus.OK);
    }

    @Operation(summary = "Removes a user privilege from this app's data",
            description = "Requester has to have DASHBOARD_ADMIN rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "App Priv Removed OK",
                    content = @Content(schema = @Schema(implementation = ScratchStorageAppRegistryEntry.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed request body / app name already exists or appId is malformed",
                    content = @Content(schema = @Schema(implementation = BadRequestException.class))),
            @ApiResponse(responseCode = "404",
                    description = "Application ID not found",
                    content = @Content(schema = @Schema(implementation = RecordNotFoundException.class))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges, or no SCRATCH_ADMIN privileges for given app id")
    })
    @DeleteMapping("/apps/{id}/user/{appPrivIdEntry}")
    public ResponseEntity<Object> removeUserPriv(
            @Parameter(name = "id", description = "Application UUID", required = true) @PathVariable UUID id,
            @Parameter(name = "appPrivIdEntry", description = "UUID of the User-Priv set to remove", required = true) @PathVariable UUID appPrivIdEntry) {

        checkUserIsDashBoardAdminOrScratchAdmin(id);
        return new ResponseEntity<>(scratchStorageService.removeUserPrivFromApp(id, appPrivIdEntry), HttpStatus.OK);
    }

    @Operation(summary = "Deletes this application from the Common API registry (removes from scratch space use)",
            description = "Requester has to have DASHBOARD_ADMIN rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "App Removed OK",
                    content = @Content(schema = @Schema(implementation = ScratchStorageAppRegistryEntry.class))),
            @ApiResponse(responseCode = "400",
                    description = "AppId is malformed",
                    content = @Content(schema = @Schema(implementation = BadRequestException.class))),
            @ApiResponse(responseCode = "404",
                    description = "Application ID not found",
                    content = @Content(schema = @Schema(implementation = RecordNotFoundException.class))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges")
    })
    @PreAuthorizeDashboardAdmin
    @DeleteMapping("/apps/{id}")
    public ResponseEntity<Object> deleteExistingAppEntry(
            @Parameter(name = "id", description = "Application UUID", required = true) @PathVariable UUID id) {

        return new ResponseEntity<>(scratchStorageService.deleteScratchStorageApp(id), HttpStatus.OK);
    }


    // scratch app user management endpoints - admins can only manage scratch space users

    @Operation(summary = "Gets the entire table of Scratch Space users (ID, email...)",
            description = "Requester has to have DASHBOARD_ADMIN rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScratchStorageUser.class)))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges")
    })
    @PreAuthorizeDashboardAdmin
    @GetMapping("/users")
    public ResponseEntity<Object> getAllUsers() {
        return new ResponseEntity<>(scratchStorageService.getAllScratchUsers(), HttpStatus.OK);
    }

    @Operation(summary = "Adds a new Scratch Space user by their P1 email address",
            description = "Requester has to have DASHBOARD_ADMIN rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201",
                    description = "New user added operation",
                    content = @Content(schema = @Schema(implementation = ScratchStorageUser.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed Scratch Storage object",
                    content = @Content(schema = @Schema(implementation = BadRequestException.class))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges"),
            @ApiResponse(responseCode = "409",
                    description = "Scratch User UUID or email address is already is use",
                    content = @Content(schema = @Schema(implementation = ResourceAlreadyExistsException.class)))
    })
    @PreAuthorizeDashboardAdmin
    @PostMapping("/users")
    public ResponseEntity<Object> addNewScratchUser(
            @Parameter(name = "user", description = "Scratch Storage User entity", required = true) @Valid @RequestBody ScratchStorageUser user) {
        return new ResponseEntity<>(scratchStorageService.addNewScratchUser(user), HttpStatus.CREATED);
    }

    @Operation(summary = "Edits an existing Scratch Space user information",
            description = "Requester has to have DASHBOARD_ADMIN rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Operation Successful",
                    content = @Content(schema = @Schema(implementation = ScratchStorageUser.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed Scratch Storage object or malformed user UUID",
                    content = @Content(schema = @Schema(implementation = BadRequestException.class))),
            @ApiResponse(responseCode = "404",
                    description = "User id not found",
                    content = @Content(schema = @Schema(implementation = RecordNotFoundException.class))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges"),
            @ApiResponse(responseCode = "409",
                    description = "Scratch User Email Address is already is use",
                    content = @Content(schema = @Schema(implementation = ResourceAlreadyExistsException.class)))
    })
    @PreAuthorizeDashboardAdmin
    @PutMapping("/users/{id}")
    public ResponseEntity<Object> editScratchUser(
            @Parameter(name = "id", description = "Scratch User Id", required = true) @PathVariable UUID id,
            @Parameter(name = "user", description = "Scratch Storage User entity", required = true) @Valid @RequestBody ScratchStorageUser user) {
        return new ResponseEntity<>(scratchStorageService.editScratchUser(id, user), HttpStatus.OK);
    }

    @Operation(summary = "Deletes a scratch user",
            description = "Requester has to have DASHBOARD_ADMIN rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Operation Successful",
                    content = @Content(schema = @Schema(implementation = ScratchStorageUser.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed user UUID",
                    content = @Content(schema = @Schema(implementation = BadRequestException.class))),
            @ApiResponse(responseCode = "404",
                    description = "User id not found",
                    content = @Content(schema = @Schema(implementation = RecordNotFoundException.class))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges")
    })
    @PreAuthorizeDashboardAdmin
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Object> deleteScratchUser(
            @Parameter(name = "id", description = "Scratch User Id", required = true) @PathVariable UUID id) {
        return new ResponseEntity<>(scratchStorageService.deleteScratchUser(id), HttpStatus.OK);
    }

    @Operation(summary = "Gets all SCRATCH space privileges available",
            description = "Gets all the SCRATCH space privileges so that privilege names can be mapped to their IDs")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Operation Successful",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = PrivilegeDto.class))))
    })
    @GetMapping("/users/privs")
    public ResponseEntity<Object> getScratchPrivs() {
        List<PrivilegeDto> scratchPrivs = Lists.newArrayList(privilegeService.getPrivileges())
                .stream()
                .filter(item -> item.getName().startsWith("SCRATCH_"))
                .collect(Collectors.toList());

        return new ResponseEntity<>(scratchPrivs, HttpStatus.OK);
    }
}
