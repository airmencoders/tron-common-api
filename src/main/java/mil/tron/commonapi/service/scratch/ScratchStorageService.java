package mil.tron.commonapi.service.scratch;

import mil.tron.commonapi.dto.ScratchStorageAppRegistryDto;
import mil.tron.commonapi.dto.ScratchStorageAppUserPrivDto;
import mil.tron.commonapi.entity.scratch.ScratchStorageAppRegistryEntry;
import mil.tron.commonapi.entity.scratch.ScratchStorageEntry;
import mil.tron.commonapi.entity.scratch.ScratchStorageUser;

import java.util.UUID;

public interface ScratchStorageService {
    Iterable<ScratchStorageEntry> getAllEntries();
    Iterable<ScratchStorageEntry> getAllEntriesByApp(UUID appId);
    ScratchStorageEntry getEntryById(UUID id);
    ScratchStorageEntry getKeyValueEntryByAppId(UUID appId, String keyName);
    ScratchStorageEntry setKeyValuePair(UUID appId, String key, String value);
    ScratchStorageEntry deleteKeyValuePair(UUID appId, String key);
    Iterable<ScratchStorageEntry> deleteAllKeyValuePairsForAppId(UUID appId);
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
    ScratchStorageAppRegistryEntry addNewScratchAppName(ScratchStorageAppRegistryEntry entry);
    ScratchStorageAppRegistryEntry editExistingScratchAppEntry(UUID id, ScratchStorageAppRegistryEntry entry);
    ScratchStorageAppRegistryEntry deleteScratchStorageApp(UUID id);
    ScratchStorageAppRegistryEntry addUserPrivToApp(UUID appId, ScratchStorageAppUserPrivDto priv);
    ScratchStorageAppRegistryEntry removeUserPrivFromApp(UUID appId, UUID appPrivIdEntry);
    ScratchStorageAppRegistryEntry setImplicitReadForApp(UUID appId, boolean implicitRead);

    // scratch storage users management
    Iterable<ScratchStorageUser> getAllScratchUsers();
    ScratchStorageUser editScratchUser(UUID id, ScratchStorageUser user);
    ScratchStorageUser addNewScratchUser(ScratchStorageUser user);
    ScratchStorageUser deleteScratchUser(UUID id);

    boolean userCanReadFromAppId(UUID appId, String email);
    boolean userCanWriteToAppId(UUID appId, String email);
    boolean userHasAdminWithAppId(UUID appId, String email);

    // JSON methods to treating values of specified keys like JSON
    String getKeyValueJson(UUID appId, String keyName, String jsonPathSpec);
    void patchKeyValueJson(UUID appId, String keyName, String value, String jsonPathSpec);
}
