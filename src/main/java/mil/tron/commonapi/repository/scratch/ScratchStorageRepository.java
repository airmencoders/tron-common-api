package mil.tron.commonapi.repository.scratch;

import mil.tron.commonapi.entity.scratch.ScratchStorageEntry;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScratchStorageRepository extends CrudRepository<ScratchStorageEntry, UUID> {
    public Iterable<ScratchStorageEntry> findAllByAppId(UUID appId);
    public Optional<ScratchStorageEntry> findByAppIdAndKey(UUID appId, String key);
    public void deleteByAppIdAndKey(UUID appId, String key);
}