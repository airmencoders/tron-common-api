package mil.tron.commonapi.dto.kpi;


import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class KpiSummaryDto {
    @Getter
    @Setter
    @Builder.Default
    @JsonIgnore
    private UUID id = UUID.randomUUID();
	
	@Getter
	@Setter
	@NotNull
	private LocalDate startDate;
	
	@Getter
	@Setter
	@NotNull
	private LocalDate endDate;
	
	@Getter
	@Setter
	private Long averageLatencyForSuccessfulRequests;
	
	@Getter
	@Setter
	@NotNull
	private Long appSourceCount;
	
	@Getter
	@Setter
	@NotNull
	private Long appClientToAppSourceRequestCount;
	
	@Getter
	@Setter
	private List<UniqueVisitorCountDto> uniqueVisitorCounts;
}
