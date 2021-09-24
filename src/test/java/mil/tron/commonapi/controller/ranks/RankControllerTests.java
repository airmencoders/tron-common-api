package mil.tron.commonapi.controller.ranks;

import com.fasterxml.jackson.databind.ObjectMapper;

import mil.tron.commonapi.dto.rank.RankCategorizedDto;
import mil.tron.commonapi.dto.rank.RankResponseWrapper;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.ranks.Rank;
import mil.tron.commonapi.service.ranks.RankService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class RankControllerTests {
    private static final String ENDPOINT = "/v1/rank/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RankService rankService;

    private List<Rank> ranks;
    private List<Rank> usafRanks;

    @BeforeEach
    public void beforeEachTest(){
        ranks = List.of(
                Rank.builder().branchType(Branch.USAF).abbreviation("Capt").build(),
                Rank.builder().branchType(Branch.USAF).abbreviation("Col").build(),
                Rank.builder().branchType(Branch.USN).abbreviation("Adm").build()
        );
        usafRanks = List.of(ranks.get(0), ranks.get(1));
    }

    @Test
    void getRanksTest() throws Exception{
        Mockito.when(rankService.getRanks())
                .thenReturn(ranks);
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(result -> 
                	assertThat(result.getResponse().getContentAsString())
                	.isEqualTo(OBJECT_MAPPER.writeValueAsString(RankResponseWrapper.builder().data(ranks).build())));
    }
    
    @Nested
    class GetRanksByBranchCategorizedTest {
        @Test
        void validBranch() throws Exception {
        	RankCategorizedDto dto =  RankCategorizedDto.builder().officer(usafRanks).build();
            Mockito.when(rankService.getRanksByBranchAndCategorize(Branch.USAF))
                    .thenReturn(dto);
            mockMvc.perform(get(ENDPOINT + "usaf/categorized"))
                    .andExpect(status().isOk())
                    .andExpect(result -> 
                    	assertThat(result.getResponse().getContentAsString())
                    	.isEqualTo(OBJECT_MAPPER.writeValueAsString(dto)));
        }

        @Test
        void invalidBranch() throws Exception {
            mockMvc.perform(get(ENDPOINT + "doesnotexist/categorized"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class GetRanksByBranchTest {
        @Test
        void validBranch() throws Exception {
            Mockito.when(rankService.getRanks(Branch.USAF))
                    .thenReturn(usafRanks);
            mockMvc.perform(get(ENDPOINT + "usaf"))
                    .andExpect(status().isOk())
                    .andExpect(result -> 
                    	assertThat(result.getResponse().getContentAsString())
                    	.isEqualTo(OBJECT_MAPPER.writeValueAsString(RankResponseWrapper.builder().data(usafRanks).build())));
        }

        @Test
        void invalidBranch() throws Exception {
            mockMvc.perform(get(ENDPOINT + "doesnotexist"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class GetRankTest{
        @Test
        void validBranch() throws Exception {
            Mockito.when(rankService.getRank("Capt", Branch.USAF))
                    .thenReturn(ranks.get(0));
            mockMvc.perform(get(ENDPOINT + "usaf/Capt"))
                    .andExpect(status().isOk())
                    .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(ranks.get(0))));
        }

        @Test
        void invalidBranch() throws Exception {
            mockMvc.perform(get(ENDPOINT + "doesnotexist/Capt"))
                    .andExpect(status().isNotFound());
        }
    }
}
