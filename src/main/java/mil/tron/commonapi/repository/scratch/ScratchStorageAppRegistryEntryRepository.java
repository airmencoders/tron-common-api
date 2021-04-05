package mil.tron.commonapi.repository.scratch;

import mil.tron.commonapi.entity.scratch.ScratchStorageAppRegistryEntry;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface ScratchStorageAppRegistryEntryRepository extends CrudRepository<ScratchStorageAppRegistryEntry, UUID> {
    public boolean existsByAppNameIgnoreCase(String appName);
    
    @Query(value = "SELECT entry "
    				+ "FROM ScratchStorageAppRegistryEntry entry, "
    				+ "IN(entry.userPrivs) privs "
    				+ "WHERE privs.user.email = :email")
    Iterable<ScratchStorageAppRegistryEntry> findAllAppsWithUserEmail(String email);
}
