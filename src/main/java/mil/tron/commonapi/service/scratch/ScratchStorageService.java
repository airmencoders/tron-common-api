package mil.tron.commonapi.service.scratch;

import mil.tron.commonapi.entity.scratch.ScratchStorageAppRegistryEntry;
import mil.tron.commonapi.entity.scratch.ScratchStorageAppUserPriv;
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

    // scratch storage app management...
    Iterable<ScratchStorageAppRegistryEntry> getAllRegisteredScratchApps();
    ScratchStorageAppRegistryEntry addNewScratchAppName(ScratchStorageAppRegistryEntry entry);
    ScratchStorageAppRegistryEntry editExistingScratchAppEntry(UUID id, ScratchStorageAppRegistryEntry entry);
    ScratchStorageAppRegistryEntry deleteScratchStorageApp(UUID id);

    // scratch storage app-to-user privs management...
    Iterable<ScratchStorageAppUserPriv> getAllAppsToUsersPrivs();
    Iterable<ScratchStorageAppUserPriv> getAllPrivsForAppId(UUID appId);
    ScratchStorageAppUserPriv addNewUserAppPrivilegeEntry(ScratchStorageAppUserPriv appUserPriv);
    ScratchStorageAppUserPriv editUserAppPrivilegeEntry(UUID id, ScratchStorageAppUserPriv appUserPriv);
    ScratchStorageAppUserPriv deleteUserAppPrivilege(UUID id);

    // scratch storage users management
    Iterable<ScratchStorageUser> getAllScratchUsers();
    ScratchStorageUser editScratchUser(UUID id, ScratchStorageUser user);
    ScratchStorageUser addNewScratchUser(ScratchStorageUser user);
    ScratchStorageUser deleteScratchUser(UUID id);
}
