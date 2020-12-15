package mil.tron.commonapi.repository;

import mil.tron.commonapi.airman.Airman;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface AirmanRepository extends CrudRepository<Airman, UUID> {

}