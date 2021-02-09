package mil.tron.commonapi.service.ranks;

import mil.tron.commonapi.entity.ranks.Rank;

import java.util.Optional;
import java.util.UUID;

public interface RankService  {

    Iterable<Rank> getRanks();
    Optional<Rank> getRank(UUID id);
}
