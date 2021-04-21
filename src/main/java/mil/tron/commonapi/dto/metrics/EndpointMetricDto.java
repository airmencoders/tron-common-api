package mil.tron.commonapi.dto.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.validation.Valid;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mil.tron.commonapi.dto.appsource.AppEndpointDto;

@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EndpointMetricDto extends AppEndpointDto {

    @Builder(builderMethodName = "endpointMetricBuilder")
    public EndpointMetricDto(UUID id, String path, String requestType, List<MeterValueDto> values) {
        super(id, path, requestType);
        this.values = values;
    }

    @Getter
    @Setter
    @Valid
    @EqualsAndHashCode.Exclude
    private List<MeterValueDto> values = new ArrayList<>();
}
