package mil.tron.commonapi.controller.scratch;

import com.google.common.collect.Lists;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.annotation.response.WrappedEnvelopeResponse;
import mil.tron.commonapi.annotation.security.PreAuthorizeDashboardAdmin;
import mil.tron.commonapi.dto.*;
import mil.tron.commonapi.exception.ExceptionResponse;
import mil.tron.commonapi.exception.InvalidScratchSpacePermissions;
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
     * Private helper to authorize a user to a scratch space identified by the app UUID for read access
     * @param appId the appid of the app's data user/request is trying to access
     * @param keyName the key name (if applicable)
     */
    private void validateScratchReadAccessForUser(UUID appId, String keyName) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = authentication.getCredentials().toString();  // get the JWT email string
        if (!scratchStorageService.userCanReadFromAppId(appId, userEmail, keyName)) {
            throw new InvalidScratchSpacePermissions(INVALID_PERMS);
        }
    }

    /**
     * Private helper to authorize a user to a scratch space identified by the app UUID for write access
     * @param appId the appid of the app's data user/request is trying to access
     * @param keyName the key name (if applicable)
     */
	private void validateScratchWriteAccessForUser(UUID appId, String keyName) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = authentication.getCredentials().toString();  // get the JWT email string
        if (!scratchStorageService.userCanWriteToAppId(appId, userEmail, keyName)) {
            throw new InvalidScratchSpacePermissions(INVALID_PERMS);
        }
    }

    /**
     * Private helper to authorize a user to a scratch space identified by the app UUID for deleting key access
     * @param appId the appid of the app's data user/request is trying to access
     * @param keyName the key name (if applicable)
     */
    private void validateScratchDeleteRightsForUser(UUID appId, String keyName) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = authentication.getCredentials().toString();  // get the JWT email string
        if (!scratchStorageService.userCanDeleteKeyForAppId(appId, userEmail, keyName)) {
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

    /**
     * Helper to say whether the requester is dashboard admin or the scratch admin of given app id
     * @param appId the scratch space UUID
     * @return true or false
     */
    private boolean userIsDashBoardAdminOrScratchAdmin(UUID appId) {

        boolean result = true;

        try {
            this.checkUserIsDashBoardAdminOrScratchAdmin(appId);
        }
        catch (InvalidScratchSpacePermissions ex) {
            result = false;
        }

        return result;
    }

    /**
     * @deprecated No longer valid T166. See {@link #getAllKeyValuePairsWrapped()} for new usage.
     * @return
     */
    @Operation(summary = "Retrieves all key-value pairs for all scratch space consuming apps",
            description = "Requires request to be under DASHBOARD_ADMIN privileges")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScratchStorageEntryDto.class)))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed request body",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @Deprecated(since = "v2")
    @PreAuthorizeDashboardAdmin  // only admin can see everyone's key-value pairs
    @GetMapping({"${api-prefix.v1}/scratch"})
    public ResponseEntity<Object> getAllKeyValuePairs() {
        return new ResponseEntity<>(scratchStorageService.getAllEntries(), HttpStatus.OK);
    }
    
    @Operation(summary = "Retrieves all key-value pairs for all scratch space consuming apps",
            description = "Requires request to be under DASHBOARD_ADMIN privileges")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = ScratchStorageEntryDtoResponseWrapper.class))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed request body",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @WrappedEnvelopeResponse
    @PreAuthorizeDashboardAdmin  // only admin can see everyone's key-value pairs
    @GetMapping({"${api-prefix.v2}/scratch"})
    public ResponseEntity<Object> getAllKeyValuePairsWrapped() {
        return new ResponseEntity<>(scratchStorageService.getAllEntries(), HttpStatus.OK);
    }

    /**
     * @deprecated No longer valid T166. See {@link #getAllKeyValuePairsForAppIdWrapped(UUID)} ()} for new usage.
     * @return
     */
    @Operation(summary = "Retrieves all key-value pairs for a single app",
            description = "App ID is the UUID of the owning application. Note if app is in ACL mode, then this endpoint" +
                    "will not work unless requester is a SCRATCH_ADMIN - since ACL mode restricts read/write on a key by" +
                    "key basis")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScratchStorageEntryDto.class)))),
            @ApiResponse(responseCode = "403",
                    description = "Insufficient privileges",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "Application ID not valid or found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed Application UUID",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @Deprecated(since = "v2")
    @GetMapping({"${api-prefix.v1}/scratch/{appId}"})
    public ResponseEntity<Object> getAllKeyValuePairsForAppId(
            @Parameter(name = "appId", description = "Application UUID", required = true) @PathVariable UUID appId) {

        validateScratchReadAccessForUser(appId,  "");
        return new ResponseEntity<>(scratchStorageService.getAllEntriesByApp(appId), HttpStatus.OK);
    }

    @Operation(summary = "Retrieves all key-value pairs for a single app",
            description = "App ID is the UUID of the owning application. Note if app is in ACL mode, then this endpoint" +
                    "will not work unless requester is a SCRATCH_ADMIN - since ACL mode restricts read/write on a key by" +
                    "key basis")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScratchStorageEntryDtoResponseWrapper.class)))),
            @ApiResponse(responseCode = "403",
                    description = "Insufficient privileges",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "Application ID not valid or found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed Application UUID",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @WrappedEnvelopeResponse
    @GetMapping({"${api-prefix.v2}/scratch/{appId}"})
    public ResponseEntity<Object> getAllKeyValuePairsForAppIdWrapped(
            @Parameter(name = "appId", description = "Application UUID", required = true) @PathVariable UUID appId) {

        validateScratchReadAccessForUser(appId,  "");
        return new ResponseEntity<>(scratchStorageService.getAllEntriesByApp(appId), HttpStatus.OK);
    }

    /**
     * @deprecated No longer valid T166. See {@link #getAllKeysForAppIdWrapped(UUID)} for new usage.
     * @param appId
     * @return
     */
    @Operation(summary = "Retrieves all keys for for a single app",
            description = "App ID is the UUID of the owning application. Note if app is in ACL mode, then this endpoint" +
                    "will not work unless requester is a SCRATCH_ADMIN - since ACL mode restricts read/write on a key by" +
                    "key basis")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = String[].class)))),
            @ApiResponse(responseCode = "403",
                    description = "Insufficient privileges",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "Application ID not valid or found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed Application UUID",
            		content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @Deprecated(since = "v2")
    @GetMapping({"${api-prefix.v1}/scratch/apps/{appId}/keys"})
    public ResponseEntity<Object> getAllKeysForAppId(
            @Parameter(name = "appId", description = "Application UUID", required = true) @PathVariable UUID appId) {

        validateScratchReadAccessForUser(appId, "");
        return new ResponseEntity<>(scratchStorageService.getAllKeysForAppId(appId), HttpStatus.OK);
    }

    @Operation(summary = "Retrieves all keys for for a single app",
            description = "App ID is the UUID of the owning application. Note if app is in ACL mode, then this endpoint" +
                    "will not work unless requester is a SCRATCH_ADMIN - since ACL mode restricts read/write on a key by" +
                    "key basis")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = GenericStringArrayResponseWrapper.class))),
            @ApiResponse(responseCode = "403",
                    description = "Insufficient privileges",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "Application ID not valid or found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed Application UUID",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @WrappedEnvelopeResponse
    @GetMapping({"${api-prefix.v2}/scratch/apps/{appId}/keys"})
    public ResponseEntity<Object> getAllKeysForAppIdWrapped(
            @Parameter(name = "appId", description = "Application UUID", required = true) @PathVariable UUID appId) {

        validateScratchReadAccessForUser(appId,  "");
        return new ResponseEntity<>(scratchStorageService.getAllKeysForAppId(appId), HttpStatus.OK);
    }
    
    @Operation(summary = "Retrieves a single key-value pair for for a single app",
            description = "App ID is the UUID of the owning application")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = ScratchStorageEntryDto.class))),
            @ApiResponse(responseCode = "403",
                    description = "Insufficient privileges",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "Application ID / Key name not valid or found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed Application UUID",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @GetMapping({"${api-prefix.v1}/scratch/{appId}/{keyName}", "${api-prefix.v2}/scratch/{appId}/{keyName}"})
    public ResponseEntity<Object> getKeyValueByKeyName(
            @Parameter(name = "appId", description = "Application UUID", required = true) @PathVariable UUID appId,
            @Parameter(name = "keyName", description = "Key Name to look up", required = true) @PathVariable String keyName) {

        validateScratchReadAccessForUser(appId, keyName);
        return new ResponseEntity<>(scratchStorageService.getKeyValueEntryByAppId(appId, keyName), HttpStatus.OK);
    }

    @Operation(summary = "Treats the key's value as JSON and returns the JsonPath query invoked onto that JSON structure. " +
                "Returns JSON string matching the specified JSON Path",
            description = "App ID is the UUID of the owning application")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "403",
                    description = "Insufficient privileges",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "Application ID / Key name not valid or found / JSON path spec not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed Application UUID / Value cannot be jsonized / Unable to serialize response to JSON",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PostMapping(value = {"${api-prefix.v1}/scratch/{appId}/{keyName}/jsonize", "${api-prefix.v2}/scratch/{appId}/{keyName}/jsonize"}, consumes = {"text/plain;charset=UTF-8"})
    public ResponseEntity<Object> getKeyValueByKeyNameAsJson(
            @Parameter(name = "appId", description = "Application UUID", required = true) @PathVariable UUID appId,
            @Parameter(name = "keyName", description = "Key Name to look up", required = true) @PathVariable String keyName,
            @Parameter(name = "jsonPath", description = "Jayway JsonPath spec string", required = true) @RequestBody String jsonPath) {

        validateScratchReadAccessForUser(appId, keyName);
        return new ResponseEntity<>(scratchStorageService.getKeyValueJson(appId, keyName, jsonPath), HttpStatus.OK);
    }

    @Operation(summary = "Treats the key's value as JSON and attempts to update a portion of it from given JSON Patch spec " +
                "with provided value.  Returns NO_CONTENT response on successful update.",
            description = "App ID is the UUID of the owning application")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204",
                    description = "Successful operation"),
            @ApiResponse(responseCode = "404",
                    description = "Application ID / Key name not valid or found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed Application UUID / Value cannot be jsonized / Bad JSON Path / Unable to serialize response to JSON",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PatchMapping({"${api-prefix.v1}/scratch/{appId}/{keyName}/jsonize", "${api-prefix.v2}/scratch/{appId}/{keyName}/jsonize"})
    public ResponseEntity<Object> patchKeyValuePairAsJson(
            @Parameter(name = "appId", description = "Application UUID", required = true) @PathVariable UUID appId,
            @Parameter(name = "keyName", description = "Key Name to look up", required = true) @PathVariable String keyName,
            @Parameter(name = "updateSpec", description = "Object specifying the json path to execute and the new value", required = true)
                @Valid @RequestBody ScratchValuePatchJsonDto valueSpec) {

        validateScratchWriteAccessForUser(appId, keyName);
        scratchStorageService.patchKeyValueJson(appId, keyName, valueSpec.getValue(), valueSpec.getJsonPath());
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Operation(summary = "Adds or updates a key-value pair for a given App Id",
            description = "SCRATCH_WRITE privileges are required for the requester for the given App Id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = ScratchStorageEntryDto.class))),
            @ApiResponse(responseCode = "403",
                    description = "Write / Update action forbidden - no WRITE privileges",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "Application ID / Key name not valid or found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed Request Body",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PostMapping({"${api-prefix.v1}/scratch", "${api-prefix.v2}/scratch"})
    public ResponseEntity<Object> setKeyValuePair(
            @Parameter(name = "entry", description = "Key-Value-AppId object", required = true) @Valid @RequestBody ScratchStorageEntryDto entry) {

        validateScratchWriteAccessForUser(entry.getAppId(), entry.getKey());

        return new ResponseEntity<>(
                scratchStorageService.setKeyValuePair(entry.getAppId(), entry.getKey(), entry.getValue()), HttpStatus.OK);
    }

    @Operation(summary = "Deletes a key-value pair for a given App Id",
            description = "SCRATCH_WRITE privileges are required for the requester for the given App Id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = ScratchStorageEntryDto.class))),
            @ApiResponse(responseCode = "403",
                    description = "Write / Update action forbidden - no WRITE privileges",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "Application ID / Key name not valid or found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed request body",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @DeleteMapping({"${api-prefix.v1}/scratch/{appId}/key/{key}", "${api-prefix.v2}/scratch/{appId}/key/{key}"})
    public ResponseEntity<Object> deleteKeyValuePair(
            @Parameter(name = "appId", description = "Application UUID", required = true) @PathVariable UUID appId,
            @Parameter(name = "key", description = "Key name of the key-value pair to delete", required = true) @PathVariable String key) {

        validateScratchDeleteRightsForUser(appId, key);
        return new ResponseEntity<>(scratchStorageService.deleteKeyValuePair(appId, key), HttpStatus.OK);
    }

    @Operation(summary = "Deletes all key-value pairs for a given App Id",
            description = "SCRATCH_WRITE privileges are required for the requester for the given App Id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = ScratchStorageEntryDto.class))),
            @ApiResponse(responseCode = "403",
                    description = "Write / Update action forbidden - no WRITE privileges",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "Application ID not valid or found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed Request Body",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @DeleteMapping({"${api-prefix.v1}/scratch/{appId}", "${api-prefix.v2}/scratch/{appId}"})
    public ResponseEntity<Object> deleteAllKeyValuePairsForAppId(
            @Parameter(name = "appId", description = "Application UUID", required = true) @PathVariable UUID appId) {

        validateScratchDeleteRightsForUser(appId, "");
        return new ResponseEntity<>(scratchStorageService.deleteAllKeyValuePairsForAppId(appId), HttpStatus.OK);
    }

    // scratch app registration/management endpoints... only admins can manage these

    /**
     * @deprecated No longer valid T166. See {@link #getScratchSpaceAppsWrapped()} for new usage.
     * @return
     */
    @Operation(summary = "Gets the entire table of Scratch Storage apps that are registered with Common API if requester" +
            "is a dashboard admin, otherwise returns the scratch space apps the requester is a scratch admin for",
            description = "Requester has to have DASHBOARD_ADMIN rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScratchStorageAppRegistryDto.class)))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @Deprecated(since = "v2")
    @GetMapping({"${api-prefix.v1}/scratch/apps"})
    public ResponseEntity<Object> getScratchSpaceApps() {
        return new ResponseEntity<>(Lists.newArrayList(scratchStorageService
                .getAllRegisteredScratchApps())
                .stream()
                .filter(item -> userIsDashBoardAdminOrScratchAdmin(item.getId()))
                .collect(Collectors.toList()), HttpStatus.OK);
    }
    
    @Operation(summary = "Gets the entire table of Scratch Storage apps that are registered with Common API if requester" +
            "is a dashboard admin, otherwise returns the scratch space apps the requester is a scratch admin for",
            description = "Requester has to have DASHBOARD_ADMIN rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = ScratchStorageAppRegistryDtoResponseWrapper.class))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @WrappedEnvelopeResponse
    @GetMapping({"${api-prefix.v2}/scratch/apps"})
    public ResponseEntity<Object> getScratchSpaceAppsWrapped() {
        return new ResponseEntity<>(Lists.newArrayList(scratchStorageService
                .getAllRegisteredScratchApps())
                .stream()
                .filter(item -> userIsDashBoardAdminOrScratchAdmin(item.getId()))
                .collect(Collectors.toList()), HttpStatus.OK);
    }
    
    @Operation(summary = "Gets all Scratch Storage apps that the current Authorized User is a user of",
    		description= "Each Scratch Storage App returned will only contain user privileges for the Authorized User. It will not contain the privileges of other users.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScratchStorageAppRegistryDto.class)))),
            @ApiResponse(responseCode = "403",
            		description = "Not Authorized",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @GetMapping({"${api-prefix.v1}/scratch/apps/self", "${api-prefix.v2}/scratch/apps/self"})
    public ResponseEntity<Object> getScratchSpaceAppsByAuthorizedUser() {
    	Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = authentication.getCredentials().toString();  // get the JWT email string
        
        return new ResponseEntity<>(scratchStorageService.getAllScratchAppsContainingUser(userEmail), HttpStatus.OK);
    }

    @Operation(summary = "Gets a single Scratch Storage app's record that is registered with Common API",
            description = "Requester has to have DASHBOARD_ADMIN rights or have SCRATCH_ADMIN rights for given app ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = ScratchStorageAppRegistryDto.class))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges, or no SCRATCH_ADMIN privilege for given app ID.",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @GetMapping({"${api-prefix.v1}/scratch/apps/{appId}", "${api-prefix.v2}/scratch/apps/{appId}"})
    public ResponseEntity<Object> getScratchAppById(@PathVariable UUID appId) {

        checkUserIsDashBoardAdminOrScratchAdmin(appId);
        return new ResponseEntity<>(scratchStorageService.getRegisteredScratchApp(appId), HttpStatus.OK);
    }

    @Operation(summary = "Adds a new Scratch Strorage consuming app name to the Common API scratch storage space",
            description = "Requester has to have DASHBOARD_ADMIN rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201",
                    description = "App Registered OK",
                    content = @Content(schema = @Schema(implementation = ScratchStorageAppRegistryDto.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed request body or app name already exists",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "App UUID or App Name is already is use",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PreAuthorizeDashboardAdmin
    @PostMapping({"${api-prefix.v1}/scratch/apps", "${api-prefix.v2}/scratch/apps"})
    public ResponseEntity<Object> postNewScratchSpaceApp(
            @Parameter(name = "entry", description = "New Application Information", required = true) @Valid @RequestBody ScratchStorageAppRegistryDto entry) {
        return new ResponseEntity<>(scratchStorageService.addNewScratchAppName(entry), HttpStatus.CREATED);
    }

    @Operation(summary = "Edit existing scratch space app information (its name)",
            description = "Requester has to have DASHBOARD_ADMIN rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "App Info Changed OK",
                    content = @Content(schema = @Schema(implementation = ScratchStorageAppRegistryDto.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed request body / app name already exists or appId is malformed",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "Application ID not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges"),
            @ApiResponse(responseCode = "409",
                    description = "App Name is already is use",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PutMapping({"${api-prefix.v1}/scratch/apps/{id}", "${api-prefix.v2}/scratch/apps/{id}"})
    public ResponseEntity<Object> editExistingAppEntry(
            @Parameter(name = "id", description = "Application UUID", required = true) @PathVariable UUID id,
            @Parameter(name = "entry", description = "Application Information Object", required = true) @Valid @RequestBody ScratchStorageAppRegistryDto entry) {
        checkUserIsDashBoardAdminOrScratchAdmin(id);
        return new ResponseEntity<>(scratchStorageService.editExistingScratchAppEntry(id, entry), HttpStatus.OK);
    }

    @Operation(summary = "Adds a user privilege to this app's data",
            description = "Requester has to have DASHBOARD_ADMIN rights, or have SCRATCH_ADMIN rights for given app ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "App Priv Added OK",
                    content = @Content(schema = @Schema(implementation = ScratchStorageAppRegistryDto.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed request body / app name already exists or appId is malformed",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "Application ID not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges, or no SCRATCH_ADMIN privileges for given app id",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "This app/user/priv combo already exists",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PatchMapping({"${api-prefix.v1}/scratch/apps/{id}/user", "${api-prefix.v2}/scratch/apps/{id}/user"})
    public ResponseEntity<Object> addUserPriv(
            @Parameter(name = "id", description = "Application UUID", required = true) @PathVariable UUID id,
            @Parameter(name = "priv", description = "Application User-Priv Object", required = true) @Valid @RequestBody ScratchStorageAppUserPrivDto priv) {

        checkUserIsDashBoardAdminOrScratchAdmin(id);
        ScratchStorageAppRegistryDto p = scratchStorageService.addUserPrivToApp(id, priv);
        return new ResponseEntity<>(p, HttpStatus.OK);
    }

    @Operation(summary = "Sets or un-sets the app's implicit read field",
            description = "Requester has to have DASHBOARD_ADMIN rights, or have SCRATCH_ADMIN rights for given app ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "App Modified OK",
                    content = @Content(schema = @Schema(implementation = ScratchStorageAppRegistryDto.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed appId or query parameter",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "Application ID not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges, or no SCRATCH_ADMIN privileges for given app id",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PatchMapping({"${api-prefix.v1}/scratch/apps/{id}/implicitRead", "${api-prefix.v2}/scratch/apps/{id}/implicitRead"})
    public ResponseEntity<Object> setImplicitReadSetting(
            @Parameter(name = "id", description = "Application UUID", required = true) @PathVariable UUID id,
            @Parameter(name = "priv", description = "Application User-Priv Object", required = true)
                @RequestParam(name = "value", required = false, defaultValue = "false") boolean implicitRead) {

        checkUserIsDashBoardAdminOrScratchAdmin(id);
        ScratchStorageAppRegistryDto p = scratchStorageService.setImplicitReadForApp(id, implicitRead);
        return new ResponseEntity<>(p, HttpStatus.OK);
    }

    @Operation(summary = "Sets or un-sets the app's ACL mode setting",
            description = "Requester has to have DASHBOARD_ADMIN rights, or have SCRATCH_ADMIN rights for given app ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "App Modified OK",
                    content = @Content(schema = @Schema(implementation = ScratchStorageAppRegistryDto.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed appId or query parameter",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "Application ID not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges, or no SCRATCH_ADMIN privileges for given app id",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PatchMapping({"${api-prefix.v1}/scratch/apps/{id}/aclMode", "${api-prefix.v2}/scratch/apps/{id}/aclMode"})
    public ResponseEntity<Object> setAclModeSetting(
            @Parameter(name = "id", description = "Application UUID", required = true)
                @PathVariable UUID id,
            @Parameter(name = "aclMode", description = "Value of the ACL Mode setting - true or false", required = true)
                @RequestParam(name = "aclMode", required = false, defaultValue = "false") boolean aclMode) {

        checkUserIsDashBoardAdminOrScratchAdmin(id);
        ScratchStorageAppRegistryDto p = scratchStorageService.setAclModeForApp(id, aclMode);
        return new ResponseEntity<>(p, HttpStatus.OK);
    }

    @Operation(summary = "Removes a user privilege from this app's data",
            description = "Requester has to have DASHBOARD_ADMIN rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "App Priv Removed OK",
                    content = @Content(schema = @Schema(implementation = ScratchStorageAppRegistryDto.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed request body / app name already exists or appId is malformed",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "Application ID not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges, or no SCRATCH_ADMIN privileges for given app id",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @DeleteMapping({"${api-prefix.v1}/scratch/apps/{id}/user/{appPrivIdEntry}", "${api-prefix.v2}/scratch/apps/{id}/user/{appPrivIdEntry}"})
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
                    content = @Content(schema = @Schema(implementation = ScratchStorageAppRegistryDto.class))),
            @ApiResponse(responseCode = "400",
                    description = "AppId is malformed",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "Application ID not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges",
            		content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PreAuthorizeDashboardAdmin
    @DeleteMapping({"${api-prefix.v1}/scratch/apps/{id}", "${api-prefix.v2}/scratch/apps/{id}"})
    public ResponseEntity<Object> deleteExistingAppEntry(
            @Parameter(name = "id", description = "Application UUID", required = true) @PathVariable UUID id) {

        return new ResponseEntity<>(scratchStorageService.deleteScratchStorageApp(id), HttpStatus.OK);
    }


    // scratch app user management endpoints - admins can only manage scratch space users

    /**
     * @deprecated No longer valid due to T166. See {@link #getAllUsersWrapped()} for new usage.
     * @return
     */
    @Operation(summary = "Gets the entire table of Scratch Space users (ID, email...)",
            description = "Requester has to have DASHBOARD_ADMIN rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
            		content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScratchStorageUserDto.class)))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges",
            		content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @Deprecated(since = "v2")
    @PreAuthorizeDashboardAdmin
    @GetMapping({"${api-prefix.v1}/scratch/users"})
    public ResponseEntity<Object> getAllUsers() {
        return new ResponseEntity<>(scratchStorageService.getAllScratchUsers(), HttpStatus.OK);
    }
    
    @Operation(summary = "Gets the entire table of Scratch Space users (ID, email...)",
            description = "Requester has to have DASHBOARD_ADMIN rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = ScratchStorageUserDtoResponseWrapper.class))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges",
            		content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PreAuthorizeDashboardAdmin
    @WrappedEnvelopeResponse
    @GetMapping({"${api-prefix.v2}/scratch/users"})
    public ResponseEntity<Object> getAllUsersWrapped() {
        return new ResponseEntity<>(scratchStorageService.getAllScratchUsers(), HttpStatus.OK);
    }

    @Operation(summary = "Adds a new Scratch Space user by their P1 email address",
            description = "Requester has to have DASHBOARD_ADMIN rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201",
                    description = "New user added operation",
                    content = @Content(schema = @Schema(implementation = ScratchStorageUserDto.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed Scratch Storage object",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges",
            		content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "Scratch User UUID or email address is already is use",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PreAuthorizeDashboardAdmin
    @PostMapping({"${api-prefix.v1}/scratch/users", "${api-prefix.v2}/scratch/users"})
    public ResponseEntity<Object> addNewScratchUser(
            @Parameter(name = "user", description = "Scratch Storage User entity", required = true) @Valid @RequestBody ScratchStorageUserDto user) {
        return new ResponseEntity<>(scratchStorageService.addNewScratchUser(user), HttpStatus.CREATED);
    }

    @Operation(summary = "Edits an existing Scratch Space user information",
            description = "Requester has to have DASHBOARD_ADMIN rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Operation Successful",
                    content = @Content(schema = @Schema(implementation = ScratchStorageUserDto.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed Scratch Storage object or malformed user UUID",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "User id not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "Scratch User Email Address is already is use",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PreAuthorizeDashboardAdmin
    @PutMapping({"${api-prefix.v1}/scratch/users/{id}", "${api-prefix.v2}/scratch/users/{id}"})
    public ResponseEntity<Object> editScratchUser(
            @Parameter(name = "id", description = "Scratch User Id", required = true) @PathVariable UUID id,
            @Parameter(name = "user", description = "Scratch Storage User entity", required = true) @Valid @RequestBody ScratchStorageUserDto user) {
        return new ResponseEntity<>(scratchStorageService.editScratchUser(id, user), HttpStatus.OK);
    }

    @Operation(summary = "Deletes a scratch user",
            description = "Requester has to have DASHBOARD_ADMIN rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Operation Successful",
                    content = @Content(schema = @Schema(implementation = ScratchStorageUserDto.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed user UUID",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "User id not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PreAuthorizeDashboardAdmin
    @DeleteMapping({"${api-prefix.v1}/scratch/users/{id}", "${api-prefix.v2}/scratch/users/{id}"})
    public ResponseEntity<Object> deleteScratchUser(
            @Parameter(name = "id", description = "Scratch User Id", required = true) @PathVariable UUID id) {
        return new ResponseEntity<>(scratchStorageService.deleteScratchUser(id), HttpStatus.OK);
    }

    /**
     * @deprecated No longer valid T166. See {@link #getScratchPrivsWrapped()} for new usage.
     * @return
     */
    @Operation(summary = "Gets all SCRATCH space privileges available",
            description = "Gets all the SCRATCH space privileges so that privilege names can be mapped to their IDs")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Operation Successful",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = PrivilegeDto.class))))
    })
    @Deprecated(since = "v2")
    @GetMapping({"${api-prefix.v1}/scratch/users/privs"})
    public ResponseEntity<Object> getScratchPrivs() {
        List<PrivilegeDto> scratchPrivs = Lists.newArrayList(privilegeService.getPrivileges())
                .stream()
                .filter(item -> item.getName().startsWith("SCRATCH_"))
                .collect(Collectors.toList());

        return new ResponseEntity<>(scratchPrivs, HttpStatus.OK);
    }
    
    @Operation(summary = "Gets all SCRATCH space privileges available",
            description = "Gets all the SCRATCH space privileges so that privilege names can be mapped to their IDs")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Operation Successful",
                    content = @Content(schema = @Schema(implementation = PrivilegeDtoResponseWrapper.class)))
    })
    @WrappedEnvelopeResponse
    @GetMapping({"${api-prefix.v2}/scratch/users/privs"})
    public ResponseEntity<Object> getScratchPrivsWrapped() {
        List<PrivilegeDto> scratchPrivs = Lists.newArrayList(privilegeService.getPrivileges())
                .stream()
                .filter(item -> item.getName().startsWith("SCRATCH_"))
                .collect(Collectors.toList());

        return new ResponseEntity<>(scratchPrivs, HttpStatus.OK);
    }
}
