package mil.tron.commonapi.controller.scratch;

import mil.tron.commonapi.annotation.security.PreAuthorizeDashboardAdmin;
import mil.tron.commonapi.dto.ScratchStorageAppUserPrivDto;
import mil.tron.commonapi.entity.scratch.ScratchStorageAppRegistryEntry;
import mil.tron.commonapi.entity.scratch.ScratchStorageEntry;
import mil.tron.commonapi.entity.scratch.ScratchStorageUser;
import mil.tron.commonapi.exception.InvalidScratchSpacePermissions;
import mil.tron.commonapi.service.scratch.ScratchStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("${api-prefix.v1}/scratch")
public class ScratchStorageController {
    private ScratchStorageService scratchStorageService;

    public ScratchStorageController(ScratchStorageService scratchStorageService) {
        this.scratchStorageService = scratchStorageService;
    }

    /**
     * Private helper to authorize a user to a scratch space identified by the app UUID
     * @param appId the appid of the app's data user/request is trying to access
     */
    private void validateScratchWriteAccessForUser(UUID appId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = authentication.getCredentials().toString();  // get the JWT email string
        if (!scratchStorageService.userCanWriteToAppId(appId, userEmail)) {
            throw new InvalidScratchSpacePermissions("Invalid User Permissions");
        }
    }

    @PreAuthorizeDashboardAdmin  // only admin can see everyone's key-value pairs
    @GetMapping("")
    public ResponseEntity<Object> getAllKeyValuePairs() {
        return new ResponseEntity<>(scratchStorageService.getAllEntries(), HttpStatus.OK);
    }

    @GetMapping("/{appId}")
    public ResponseEntity<Object> getAllKeyValuePairsForAppId(@PathVariable UUID appId) {
        return new ResponseEntity<>(scratchStorageService.getAllEntriesByApp(appId), HttpStatus.OK);
    }

    @GetMapping("/{appId}/{keyName}")
    public ResponseEntity<Object> getKeyValueByKeyName(@PathVariable UUID appId, @PathVariable String keyName) {
        return new ResponseEntity<>(scratchStorageService.getKeyValueEntryByAppId(appId, keyName), HttpStatus.OK);
    }

    @PostMapping("")
    public ResponseEntity<Object> setKeyValuePair(@Valid @RequestBody ScratchStorageEntry entry) {
        validateScratchWriteAccessForUser(entry.getAppId());

        return new ResponseEntity<>(
                scratchStorageService.setKeyValuePair(entry.getAppId(), entry.getKey(), entry.getValue()), HttpStatus.OK);
    }

    @DeleteMapping("/{appId}/{key}")
    public ResponseEntity<Object> deleteKeyValuePair(@PathVariable UUID appId, @PathVariable String key) {
        validateScratchWriteAccessForUser(appId);

        return new ResponseEntity<>(scratchStorageService.deleteKeyValuePair(appId, key), HttpStatus.OK);
    }

    @DeleteMapping("/{appId}")
    public ResponseEntity<Object> deleteAllKeyValuePairsForAppId(@PathVariable UUID appId) {
        validateScratchWriteAccessForUser(appId);

        return new ResponseEntity<>(scratchStorageService.deleteAllKeyValuePairsForAppId(appId), HttpStatus.OK);
    }

    // scratch app registration/management endpoints... only admins can manage these

    /**
     * Gets the entire table of Scratch Storage apps that are registered with Common API
     * @return returns list of the appnames and the UUIDs they are registered under
     */
    @PreAuthorizeDashboardAdmin
    @GetMapping("/apps")
    public ResponseEntity<Object> getScratchSpaceApps() {
        return new ResponseEntity<>(scratchStorageService.getAllRegisteredScratchApps(), HttpStatus.OK);
    }

    @PreAuthorizeDashboardAdmin
    @PostMapping("/apps")
    public ResponseEntity<Object> postNewScratchSpaceApp(@Valid @RequestBody ScratchStorageAppRegistryEntry entry) {
        return new ResponseEntity<>(scratchStorageService.addNewScratchAppName(entry), HttpStatus.CREATED);
    }

    @PreAuthorizeDashboardAdmin
    @PutMapping("/apps/{id}")
    public ResponseEntity<Object> editExistingAppEntry(@PathVariable UUID id, @Valid @RequestBody ScratchStorageAppRegistryEntry entry) {
        return new ResponseEntity<>(scratchStorageService.editExistingScratchAppEntry(id, entry), HttpStatus.OK);
    }

    @PreAuthorizeDashboardAdmin
    @PatchMapping("/apps/{id}/user")
    public ResponseEntity<Object> addUserPriv(@PathVariable UUID id, @Valid @RequestBody ScratchStorageAppUserPrivDto priv) {
        ScratchStorageAppRegistryEntry p = scratchStorageService.addUserPrivToApp(id, priv);
        return new ResponseEntity<>(p, HttpStatus.OK);
    }

    @PreAuthorizeDashboardAdmin
    @DeleteMapping("/apps/{id}/user/{appPrivIdEntry}")
    public ResponseEntity<Object> removeUserPriv(@PathVariable UUID id, @PathVariable UUID appPrivIdEntry) {
        return new ResponseEntity<>(scratchStorageService.removeUserPrivFromApp(id, appPrivIdEntry), HttpStatus.OK);
    }

    @PreAuthorizeDashboardAdmin
    @DeleteMapping("/apps/{id}")
    public ResponseEntity<Object> deleteExistingAppEntry(@PathVariable UUID id) {
        return new ResponseEntity<>(scratchStorageService.deleteScratchStorageApp(id), HttpStatus.OK);
    }


    // scratch app user management endpoints - admins can only manage scratch space users

    @PreAuthorizeDashboardAdmin
    @GetMapping("/users")
    public ResponseEntity<Object> getAllUsers() {
        return new ResponseEntity<>(scratchStorageService.getAllScratchUsers(), HttpStatus.OK);
    }

    @PreAuthorizeDashboardAdmin
    @PostMapping("/users")
    public ResponseEntity<Object> addNewScratchUser(@Valid @RequestBody ScratchStorageUser user) {
        return new ResponseEntity<>(scratchStorageService.addNewScratchUser(user), HttpStatus.CREATED);
    }

    @PreAuthorizeDashboardAdmin
    @PutMapping("/users/{id}")
    public ResponseEntity<Object> editScratchUser(@PathVariable UUID id, @Valid @RequestBody ScratchStorageUser user) {
        return new ResponseEntity<>(scratchStorageService.editScratchUser(id, user), HttpStatus.OK);
    }

    @PreAuthorizeDashboardAdmin
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Object> deleteScratchUser(@PathVariable UUID id) {
        return new ResponseEntity<>(scratchStorageService.deleteScratchUser(id), HttpStatus.OK);
    }
}
