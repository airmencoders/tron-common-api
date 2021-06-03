package mil.tron.commonapi.service.scratch;

import mil.tron.commonapi.dto.ScratchStorageAppRegistryDto;
import mil.tron.commonapi.dto.ScratchStorageAppUserPrivDto;
import mil.tron.commonapi.dto.ScratchStorageEntryDto;
import mil.tron.commonapi.dto.ScratchStorageUserDto;
import mil.tron.commonapi.entity.scratch.ScratchStorageAppRegistryEntry;

import java.util.UUID;

public interface ScratchStorageService {
    Iterable<ScratchStorageEntryDto> getAllEntries();
    Iterable<ScratchStorageEntryDto> getAllEntriesByApp(UUID appId);
    ScratchStorageEntryDto getEntryById(UUID id);
    ScratchStorageEntryDto getKeyValueEntryByAppId(UUID appId, String keyName);
    ScratchStorageEntryDto setKeyValuePair(UUID appId, String key, String value);
    ScratchStorageEntryDto deleteKeyValuePair(UUID appId, String key);
    Iterable<ScratchStorageEntryDto> deleteAllKeyValuePairsForAppId(UUID appId);
    Iterable<String> getAllKeysForAppId(UUID appId);

    // scratch storage app management...
    Iterable<ScratchStorageAppRegistryDto> getAllRegisteredScratchApps();
    /**
     * Returns ScratchStorageAppRegistryDtos that contain only the list of privileges 
     * for the Authorized User (does not return privileges belonging to another user)
     * 
     * @return list of Scratch Storage Apps that the Authorized User has an association with
     */
    Iterable<ScratchStorageAppRegistryDto> getAllScratchAppsContainingUser(String userEmail);
    ScratchStorageAppRegistryDto getRegisteredScratchApp(UUID appId);
    ScratchStorageAppRegistryDto addNewScratchAppName(ScratchStorageAppRegistryDto entry);
    ScratchStorageAppRegistryDto editExistingScratchAppEntry(UUID id, ScratchStorageAppRegistryDto entry);
    ScratchStorageAppRegistryDto deleteScratchStorageApp(UUID id);
    ScratchStorageAppRegistryDto addUserPrivToApp(UUID appId, ScratchStorageAppUserPrivDto priv);
    ScratchStorageAppRegistryDto removeUserPrivFromApp(UUID appId, UUID appPrivIdEntry);
    ScratchStorageAppRegistryDto setImplicitReadForApp(UUID appId, boolean implicitRead);
    ScratchStorageAppRegistryDto setAclModeForApp(UUID appId, boolean aclMode);

    // scratch storage users management
    Iterable<ScratchStorageUserDto> getAllScratchUsers();
    ScratchStorageUserDto editScratchUser(UUID id, ScratchStorageUserDto user);
    ScratchStorageUserDto addNewScratchUser(ScratchStorageUserDto newUser);
    ScratchStorageUserDto deleteScratchUser(UUID id);

    boolean userCanReadFromAppId(UUID appId, String email, String keyName);
    boolean userCanWriteToAppId(UUID appId, String email, String keyName);
    boolean userHasAdminWithAppId(UUID appId, String email);
    boolean userCanDeleteKeyForAppId(UUID appId, String email, String keyName);
    boolean aclLookup(ScratchStorageAppRegistryEntry appEntry, String email, String keyName, String desiredRole);

    // JSON methods to treating values of specified keys like JSON
    String getKeyValueJson(UUID appId, String keyName, String jsonPathSpec);
    void patchKeyValueJson(UUID appId, String keyName, String value, String jsonPathSpec);
}
