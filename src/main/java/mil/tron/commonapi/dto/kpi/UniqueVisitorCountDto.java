package mil.tron.commonapi.dto.kpi;

import java.util.UUID;

import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mil.tron.commonapi.entity.kpi.VisitorType;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class UniqueVisitorCountDto {
    @Getter
    @Setter
    @Builder.Default
    @JsonIgnore
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
