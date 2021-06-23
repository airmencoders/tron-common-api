package mil.tron.commonapi.repository.scratch;

import mil.tron.commonapi.entity.scratch.ScratchStorageAppRegistryEntry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface ScratchStorageAppRegistryEntryRepository extends JpaRepository<ScratchStorageAppRegistryEntry, UUID> {
    public boolean existsByAppNameIgnoreCase(String appName);
    
    @Query(value = "SELECT DISTINCT entry "
			+ "FROM ScratchStorageAppRegistryEntry entry, "
			+ "IN(entry.userPrivs) privs "
			+ "WHERE privs.user.email = :email")
    Iterable<ScratchStorageAppRegistryEntry> findAllAppsWithUserEmail(String email);
}
