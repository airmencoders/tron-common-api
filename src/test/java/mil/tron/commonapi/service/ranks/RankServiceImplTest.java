package mil.tron.commonapi.service.ranks;

import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.ranks.Rank;
import mil.tron.commonapi.repository.ranks.RankRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
    void getRankByIdTest(){
        UUID id = UUID.randomUUID();
        Mockito.when(rankRepository.findById(id))
                .thenReturn(Optional.of(Rank.builder()
                        .id(id)
                        .abbreviation("Gen")
                        .build()));

        Optional<Rank> result = rankService.getRank(id);

        assertThat(result.get().getId()).isEqualTo(id);
        assertThat(result.get().getAbbreviation()).isEqualTo("Gen");
    }

    @Test
    void getRankTest(){
        Mockito.when(rankRepository.findByAbbreviationAndBranchType("Gen", Branch.USAF))
                .thenReturn(Optional.of(Rank.builder()
                        .abbreviation("Gen")
                        .branchType(Branch.USAF)
                        .build()));

        Optional<Rank> result = rankService.getRank("Gen", Branch.USAF);

        assertThat(result.get().getAbbreviation()).isEqualTo("Gen");
        assertThat(result.get().getBranchType()).isEqualTo(Branch.USAF);
    }
}
