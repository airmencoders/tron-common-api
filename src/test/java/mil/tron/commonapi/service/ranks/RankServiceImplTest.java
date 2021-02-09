package mil.tron.commonapi.service.ranks;

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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
