package mil.tron.commonapi.service.ranks;

import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.ranks.Rank;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.ranks.RankRepository;
import org.springframework.stereotype.Service;

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
    public Rank getRank(UUID id) {
        return repository.findById(id).orElseThrow(() -> new RecordNotFoundException("Rank resource with ID: " + id + " does not exist."));
    }

    @Override
    public Rank getRank(String abbreviation, Branch branch) {
        return repository.findByAbbreviationAndBranchType(abbreviation, branch).orElseThrow(() -> new RecordNotFoundException(branch.name() + " Rank '" + abbreviation + "' does not exist."));
    }
}
