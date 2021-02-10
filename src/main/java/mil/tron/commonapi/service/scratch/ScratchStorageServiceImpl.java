package mil.tron.commonapi.service.scratch;

import com.google.common.collect.Lists;
import mil.tron.commonapi.entity.scratch.ScratchStorageEntry;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.scratch.ScratchStorageRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ScratchStorageServiceImpl implements ScratchStorageService {

    private ScratchStorageRepository repository;

    public ScratchStorageServiceImpl(ScratchStorageRepository repository) {
        this.repository = repository;
    }

    @Override
    public Iterable<ScratchStorageEntry> getAllEntries() {
        return repository.findAll();
    }

    @Override
    public Iterable<ScratchStorageEntry> getAllEntriesByApp(UUID appId) {
        return repository.findAllByAppId(appId);
    }

    @Override
    public ScratchStorageEntry getEntryById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new RecordNotFoundException("Cannot find record with that UUID"));
    }

    @Override
    public ScratchStorageEntry getKeyValueEntryByAppId(UUID appId, String keyName) {
        return repository.findByAppIdAndKey(appId, keyName).orElseThrow(() -> new RecordNotFoundException("Cannot find record with that AppId/Key Name"));
    }

    /**
     * Mimics adding/setting values in a HashMap, if key/value doesn't exist then its added, otherwise updated
     * @param appId the UUID of the application which this key value pair is for
     * @param key the String key of the key value pair
     * @param value the String value
     * @return the persisted/updated ScratchStorageEntry entity
     */
    @Override
    public ScratchStorageEntry setKeyValuePair(UUID appId, String key, String value) {
        Optional<ScratchStorageEntry> entry = repository.findByAppIdAndKey(appId, key);

        if (entry.isPresent()) {
            // update
            ScratchStorageEntry existingEntry = entry.get();
            existingEntry.setValue(value);
            return repository.save(existingEntry);
        }
        else {
            // create new
            ScratchStorageEntry newEntry = ScratchStorageEntry
                    .builder()
                    .id(UUID.randomUUID())
                    .appId(appId)
                    .key(key)
                    .value(value)
                    .build();

            return repository.save(newEntry);
        }
    }

    @Override
    public ScratchStorageEntry deleteKeyValuePair(UUID appId, String key) {
        ScratchStorageEntry entry = repository.findByAppIdAndKey(appId, key).orElseThrow(() ->
                new RecordNotFoundException("Cannot delete specified record, record not found"));

        repository.deleteByAppIdAndKey(appId, key);

        // return the entity deleted
        return entry;
    }

    @Override
    public Iterable<ScratchStorageEntry> deleteAllKeyValuePairsForAppId(UUID appId) {

        List<ScratchStorageEntry> deletedEntries = new ArrayList<>();
        List<ScratchStorageEntry> entries = Lists.newArrayList(repository.findAllByAppId(appId));
        for (ScratchStorageEntry entry : entries) {
            repository.deleteById(entry.getId());
            deletedEntries.add(entry);
        }

        // return all key-values deleted
        return deletedEntries;
    }
}
