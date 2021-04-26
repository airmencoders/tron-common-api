package mil.tron.commonapi.dto.metrics;

import java.util.UUID;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EndpointCountMetricDto extends CountMetricDto {

    @Getter
    @Setter
    private String method;

    @Builder(builderMethodName = "endpointCountMetricBuilder")
    public EndpointCountMetricDto(UUID id, String path, Double sum, String method) {
        super(id, path, sum);
        this.method = method;
    }

}
