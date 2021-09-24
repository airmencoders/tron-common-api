package mil.tron.commonapi.service.ranks;

import mil.tron.commonapi.dto.rank.RankCategorizedDto;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.ranks.Rank;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.ranks.RankRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class RankServiceImpl implements RankService {
	private static final String ALL_BUT_NUMBERS_REGEX = "[^\\d]";
	
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
    public RankCategorizedDto getRanksByBranchAndCategorize(Branch branch) {
    	var ranks = repository.findAllByBranchType(branch);
    	
    	List<Rank> enlisted = new ArrayList<>();
    	List<Rank> warrantOfficer = new ArrayList<>();
    	List<Rank> officer = new ArrayList<>();
    	List<Rank> civilService = new ArrayList<>();
    	List<Rank> other = new ArrayList<>();
    	
    	ranks.forEach(rank -> {
    		String payGrade = rank.getPayGrade().toUpperCase();
    		
    		if (payGrade.startsWith("E-")) {
    			enlisted.add(rank);
    			return;
    		}
    		
    		if (payGrade.startsWith("W-")) {
    			warrantOfficer.add(rank);
    			return;
    		}
    		
    		if (payGrade.startsWith("O-")) {
    			officer.add(rank);
    			return;
    		}
    		
    		if (payGrade.startsWith("GS") || payGrade.startsWith("SES")) {
    			civilService.add(rank);
    			return;
    		}
    		
    		other.add(rank);
    	});
    	
    	enlisted.sort(compareRankPayGrade());
    	warrantOfficer.sort(compareRankPayGrade());
    	officer.sort(compareRankPayGrade());
    	
    	return RankCategorizedDto.builder()
    			.enlisted(enlisted.isEmpty() ? null : enlisted)
    			.civilService(civilService.isEmpty() ? null : civilService)
    			.officer(officer.isEmpty() ? null : officer)
    			.warrantOfficer(warrantOfficer.isEmpty() ? null : warrantOfficer)
    			.other(other.isEmpty() ? null : other)
    			.build();
    }
    
    private Comparator<Rank> compareRankPayGrade() {
    	return Comparator.comparingInt(item -> {
    		try {
    			String numbersOnly = item.getPayGrade().replaceAll(ALL_BUT_NUMBERS_REGEX, "");
    			
    			if (numbersOnly.length() > 0) {
    				return Integer.valueOf(numbersOnly);
    			}
    			
    			return 0;
    		} catch (NumberFormatException ex) {
    			return 0;
    		}
    	});
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
