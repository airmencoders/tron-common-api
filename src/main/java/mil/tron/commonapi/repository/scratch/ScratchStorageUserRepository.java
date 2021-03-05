package mil.tron.commonapi.repository.scratch;

import mil.tron.commonapi.entity.scratch.ScratchStorageUser;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScratchStorageUserRepository extends CrudRepository<ScratchStorageUser, UUID> {
    public boolean existsByEmailIgnoreCase(String email);
    public Optional<ScratchStorageUser> findByEmailIgnoreCase(String email);
}
