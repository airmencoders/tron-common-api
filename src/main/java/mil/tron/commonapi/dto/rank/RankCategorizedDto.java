package mil.tron.commonapi.dto.rank;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mil.tron.commonapi.entity.ranks.Rank;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class RankCategorizedDto {
	private List<Rank> enlisted;
	private List<Rank> warrantOfficer;
	private List<Rank> officer;
	private List<Rank> civilService;
	private List<Rank> other;
}
