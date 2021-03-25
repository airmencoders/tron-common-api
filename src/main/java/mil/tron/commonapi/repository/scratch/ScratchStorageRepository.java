package mil.tron.commonapi.repository.scratch;

import mil.tron.commonapi.entity.scratch.ScratchStorageEntry;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import javax.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;

public interface ScratchStorageRepository extends CrudRepository<ScratchStorageEntry, UUID> {

    // get all key-value pairs for given appId
    Iterable<ScratchStorageEntry> findAllByAppId(UUID appId);

    // returns key-value pair for given appId and key name
    Optional<ScratchStorageEntry> findByAppIdAndKey(UUID appId, String key);

    // returns just keys for given appId
    @Query(value = "select key from scratch_storage where app_id = :appId", nativeQuery = true)
    Iterable<String> findAllKeysForAppId(UUID appId);

    @Transactional
    void deleteByAppIdAndKey(UUID appId, String key);
}
