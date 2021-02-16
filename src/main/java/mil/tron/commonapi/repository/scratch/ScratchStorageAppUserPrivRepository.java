package mil.tron.commonapi.repository.scratch;

import mil.tron.commonapi.entity.scratch.ScratchStorageAppUserPriv;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface ScratchStorageAppUserPrivRepository extends CrudRepository<ScratchStorageAppUserPriv, UUID> {
    public Iterable<ScratchStorageAppUserPriv> findAllByAppId(UUID appId);
}
