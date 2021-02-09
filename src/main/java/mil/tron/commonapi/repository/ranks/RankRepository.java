package mil.tron.commonapi.repository.ranks;

import mil.tron.commonapi.entity.ranks.Rank;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface RankRepository extends CrudRepository<Rank, UUID> {

}
