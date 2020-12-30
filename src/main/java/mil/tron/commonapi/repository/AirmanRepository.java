package mil.tron.commonapi.repository;

import mil.tron.commonapi.entity.Airman;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface AirmanRepository extends CrudRepository<Airman, UUID> {
    public Optional<Airman> findByEmailIgnoreCase(String email);
}
