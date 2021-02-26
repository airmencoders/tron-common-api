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

    // scratch storage app management...
    Iterable<ScratchStorageAppRegistryDto> getAllRegisteredScratchApps();
    ScratchStorageAppRegistryEntry addNewScratchAppName(ScratchStorageAppRegistryEntry entry);
    ScratchStorageAppRegistryEntry editExistingScratchAppEntry(UUID id, ScratchStorageAppRegistryEntry entry);
    ScratchStorageAppRegistryEntry deleteScratchStorageApp(UUID id);
    ScratchStorageAppRegistryEntry addUserPrivToApp(UUID appId, ScratchStorageAppUserPrivDto priv);
    ScratchStorageAppRegistryEntry removeUserPrivFromApp(UUID appId, UUID appPrivIdEntry);

    // scratch storage users management
    Iterable<ScratchStorageUser> getAllScratchUsers();
    ScratchStorageUser editScratchUser(UUID id, ScratchStorageUser user);
    ScratchStorageUser addNewScratchUser(ScratchStorageUser user);
    ScratchStorageUser deleteScratchUser(UUID id);
    boolean userCanWriteToAppId(UUID appId, String email);
}