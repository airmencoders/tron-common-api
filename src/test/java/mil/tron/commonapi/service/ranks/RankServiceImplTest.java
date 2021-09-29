package mil.tron.commonapi.service.ranks;

import mil.tron.commonapi.dto.rank.RankCategorizedDto;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.ranks.Rank;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.ranks.RankRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
public class RankServiceImplTest {
    @Mock
    private RankRepository rankRepository;

    @InjectMocks
    private RankServiceImpl rankService;

    @Test
    void getRanksTest(){
        Mockito.when(rankRepository.findAll())
                .thenReturn(List.of(new Rank(), new Rank(), new Rank()));

        Iterable<Rank> result = rankService.getRanks();

        assertThat(result).hasSize(3);
    }

    @Test
    void getRanksForBranchTest(){
        Mockito.when(rankRepository.findAllByBranchType(Branch.USAF))
                .thenReturn(List.of(new Rank(), new Rank(), new Rank()));

        Iterable<Rank> result = rankService.getRanks(Branch.USAF);

        assertThat(result).hasSize(3);
    }
    
    @Test
    void getRanksByBranchAndCategorize() {
    	List<Rank> enlisted = new ArrayList<>();
    	List<Rank> warrantOfficer = new ArrayList<>();
    	List<Rank> officer = new ArrayList<>();
    	List<Rank> civilService = new ArrayList<>();
    	List<Rank> other = new ArrayList<>();
    	
    	enlisted.add(Rank.builder().payGrade("E-1").build());
    	enlisted.add(Rank.builder().payGrade("E-10").build());
    	
    	warrantOfficer.add(Rank.builder().payGrade("W-1").build());
    	warrantOfficer.add(Rank.builder().payGrade("W-10").build());
    	
    	officer.add(Rank.builder().payGrade("O-1").build());
    	officer.add(Rank.builder().payGrade("O-10").build());
    	
    	civilService.add(Rank.builder().payGrade("GS").build());
    	civilService.add(Rank.builder().payGrade("SES").build());
    	
    	other.add(Rank.builder().payGrade("CTR").build());
    	
    	List<Rank> combinedRanks = Stream.of(enlisted, warrantOfficer, officer, civilService, other)
    			.flatMap(Collection::stream)
    			.collect(Collectors.toList());
    	
    	// Shuffle to randomize the list of ranks
    	Collections.shuffle(combinedRanks);
    	
    	Mockito.when(rankRepository.findAllByBranchType(Branch.USAF))
        	.thenReturn(combinedRanks);
    	
    	RankCategorizedDto dto = rankService.getRanksByBranchAndCategorize(Branch.USAF);
    	
    	// The lists should be in order by Pay Grade
    	assertThat(dto.getEnlisted())
	    	.usingRecursiveComparison()
	    	.asList()
	    	.containsExactlyElementsOf(enlisted);
    	
    	assertThat(dto.getOfficer())
	    	.usingRecursiveComparison()
	    	.asList()
	    	.containsExactlyElementsOf(officer);
    	
    	assertThat(dto.getWarrantOfficer())
	    	.usingRecursiveComparison()
	    	.asList()
	    	.containsExactlyElementsOf(warrantOfficer);
    	
    	assertThat(dto.getCivilService())
	    	.usingRecursiveComparison()
	    	.asList()
	    	.containsExactlyInAnyOrderElementsOf(civilService);
    	
    	assertThat(dto.getOther())
	    	.usingRecursiveComparison()
	    	.asList()
	    	.containsExactlyInAnyOrderElementsOf(other);
    	
    	Mockito.when(rankRepository.findAllByBranchType(Branch.USAF))
    		.thenReturn(List.of());
    	
    	dto = rankService.getRanksByBranchAndCategorize(Branch.USAF);
    	
    	// Empty lists should be null
    	assertThat(dto)
    	.usingRecursiveComparison()
    	.isEqualTo(RankCategorizedDto.builder()
    			.civilService(null)
    			.enlisted(null)
    			.officer(null)
    			.other(null)
    			.warrantOfficer(null)
    			.build());
    }

    @Nested
    class GetRankByIdTest {
        @Test
        void validId() {
            UUID id = UUID.randomUUID();
            Mockito.when(rankRepository.findById(id))
                    .thenReturn(Optional.of(Rank.builder()
                            .id(id)
                            .abbreviation("Gen")
                            .build()));

            Rank result = rankService.getRank(id);

            assertThat(result.getId()).isEqualTo(id);
            assertThat(result.getAbbreviation()).isEqualTo("Gen");
        }
        @Test
        void invalidId() {
            Mockito.when(rankRepository.findById(Mockito.any()))
                    .thenReturn(Optional.empty());

            assertThrows(RecordNotFoundException.class, () -> rankService.getRank(UUID.randomUUID()));
        }
    }

    @Nested
    class GetRankTest {
        @Test
        void validRank() {
            Mockito.when(rankRepository.findByAbbreviationAndBranchType("Gen", Branch.USAF))
                    .thenReturn(Optional.of(Rank.builder()
                            .abbreviation("Gen")
                            .branchType(Branch.USAF)
                            .build()));

            Rank result = rankService.getRank("Gen", Branch.USAF);

            assertThat(result.getAbbreviation()).isEqualTo("Gen");
            assertThat(result.getBranchType()).isEqualTo(Branch.USAF);
        }

        @Test
        void invalidRank() {
            Mockito.when(rankRepository.findByAbbreviationAndBranchType(Mockito.any(), Mockito.any()))
                    .thenReturn(Optional.empty());

            assertThrows(RecordNotFoundException.class, () -> rankService.getRank("does not exist", Branch.OTHER));
        }
    }
}
