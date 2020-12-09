package mil.tron.commonapi.repository;

import mil.tron.commonapi.airman.Airman;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface AirmanRepository extends CrudRepository<Airman, Long> {

    // override the default one since it would expect a Long
    public Airman findById(UUID id);

    // override the default one since it would expect a Long
    public boolean existsById(UUID id);

    // override the default one since it would expect a Long
    public void deleteById(UUID id);

}
