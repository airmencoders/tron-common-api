package mil.tron.commonapi.dto.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

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
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Getter
    @Setter
    @NotBlank
    @NotNull
    private String path;

    @Getter
    @Setter
    @NotBlank
    @NotNull
    private String requestType;

    @Getter
    @Setter
    @Builder.Default
    @Valid
    @EqualsAndHashCode.Exclude
    private List<MeterValueDto> values = new ArrayList<>();
}
