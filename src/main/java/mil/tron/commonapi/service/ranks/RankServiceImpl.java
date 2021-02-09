package mil.tron.commonapi.service.ranks;

import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.ranks.Rank;
import mil.tron.commonapi.repository.ranks.RankRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class RankServiceImpl implements RankService {
    private RankRepository repository;

    public RankServiceImpl(RankRepository repository) {
        this.repository = repository;
    }

    @Override
    public Iterable<Rank> getRanks() {
        return repository.findAll();
    }

    @Override
    public Iterable<Rank> getRanks(Branch branch) {
        return repository.findAllByBranchType(branch);
    }

    @Override
    public Optional<Rank> getRank(UUID id) {
        return repository.findById(id);
    }

    @Override
    public Optional<Rank> getRank(String abbreviation, Branch branch) {
        return repository.findByAbbreviationAndBranchType(abbreviation, branch);
    }
}
