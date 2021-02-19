package mil.tron.commonapi.repository.scratch;

import mil.tron.commonapi.entity.scratch.ScratchStorageAppRegistryEntry;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface ScratchStorageAppRegistryEntryRepository extends CrudRepository<ScratchStorageAppRegistryEntry, UUID> {
    public boolean existsByAppNameIgnoreCase(String appName);
}
