package mil.tron.commonapi.entity.kpi;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

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
@Entity
@Table(name="kpi_summary")
public class KpiSummary {
	@Id
    @Getter
    @Setter
    @Builder.Default
    private UUID id = UUID.randomUUID();
	
	@Getter
	@Setter
	@Column(unique=true)
	@NotNull
	private Date startDate;
	
	@Getter
	@Setter
	@Column(unique=true)
	@NotNull
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
	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
	private List<UniqueVisitorCount> uniqueVisitorCounts;
}
