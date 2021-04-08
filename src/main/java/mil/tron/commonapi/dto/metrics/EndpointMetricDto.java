package mil.tron.commonapi.dto.metrics;

import java.util.UUID;

import javax.validation.Valid;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
public class EndpointMetricDto {
        
    @Getter
    @Setter
    @Valid
    private UUID endpoint;

    @Getter
    @Setter
    private Integer count;
}
