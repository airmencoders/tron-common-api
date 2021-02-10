package mil.tron.commonapi.service.scratch;

import mil.tron.commonapi.entity.scratch.ScratchStorageEntry;

import java.util.UUID;

public interface ScratchStorageService {
    Iterable<ScratchStorageEntry> getAllEntries();
    Iterable<ScratchStorageEntry> getAllEntriesByApp(UUID appId);
    ScratchStorageEntry getEntryById(UUID id);
    ScratchStorageEntry getKeyValueEntryByAppId(UUID appId, String keyName);
    ScratchStorageEntry setKeyValuePair(UUID appId, String key, String value);
    ScratchStorageEntry deleteKeyValuePair(UUID appId, String key);
    Iterable<ScratchStorageEntry> deleteAllKeyValuePairsForAppId(UUID appId);
}
