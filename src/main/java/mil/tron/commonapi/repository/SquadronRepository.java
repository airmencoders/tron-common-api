package mil.tron.commonapi.repository;

import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.entity.Squadron;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;


public interface SquadronRepository extends CrudRepository<Squadron, UUID> {
    Optional<Organization> findByNameIgnoreCase(String name);
}
