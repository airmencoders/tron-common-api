package mil.tron.commonapi.service.ranks;

import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.ranks.Rank;

import java.util.UUID;

public interface RankService  {

    Iterable<Rank> getRanks();
    Iterable<Rank> getRanks(Branch branch);
    Rank getRank(UUID id);
    Rank getRank(String abbreviation, Branch branch);
}
