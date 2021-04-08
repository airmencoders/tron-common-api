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
@EqualsAndHashCode
public class AppSourceMetricDto {

    @Getter
    @Setter
    private UUID id;
    
    @Getter
    @Setter
    @Builder.Default
    @Valid
    private List<EndpointMetricDto> endpoints = new ArrayList<>();
}
