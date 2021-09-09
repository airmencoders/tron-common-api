package mil.tron.commonapi.entity.kpi;


import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
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
public class UniqueVisitorCount {
	@Id
    @Getter
    @Setter
    @Builder.Default
    private UUID id = UUID.randomUUID();
	
	@Getter
	@Setter
	@Enumerated(EnumType.STRING)
	@NotNull
	private VisitorType visitorType;
	
	@Getter
	@Setter
	@NotNull
	private Long uniqueCount;
	
	@Getter
	@Setter
	@NotNull
	private Long requestCount;
}
