package mil.tron.commonapi.dto.kpi;


import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;
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
	@JsonFormat(pattern = "yyyy-MM-dd", timezone = "UTC")
	@Schema(type="string", format = "date")
	private Date startDate;
	
	@Getter
	@Setter
	@NotNull
	@JsonFormat(pattern = "yyyy-MM-dd", timezone = "UTC")
	@Schema(type="string", format = "date")
	private Date endDate;
	
	@Getter
	@Setter
	private Double averageLatencyForSuccessfulRequests;
	
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
