package mil.tron.commonapi.dto.metrics;

import java.util.ArrayList;
import java.util.List;
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
@Getter
@Setter
@EqualsAndHashCode
public class EndpointMetricDto {

    @Builder.Default
    private UUID id = UUID.randomUUID();

    private String path;
    
    @Builder.Default
    @Valid
    @EqualsAndHashCode.Exclude
    private List<MeterValueDto> values = new ArrayList<>();
}
