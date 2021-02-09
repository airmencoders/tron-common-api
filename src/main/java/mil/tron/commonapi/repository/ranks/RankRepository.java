package mil.tron.commonapi.repository.ranks;

import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.ranks.Rank;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RankRepository extends CrudRepository<Rank, UUID> {
    Iterable<Rank> findAllByBranchType(Branch branchType);
    Optional<Rank> findByAbbreviationAndBranchType(String abbreviation, Branch branchType);
}
