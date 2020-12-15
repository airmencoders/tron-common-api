package mil.tron.commonapi.repository;

import mil.tron.commonapi.squadron.Squadron;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;


public interface SquadronRepository extends CrudRepository<Squadron, UUID> {

}
