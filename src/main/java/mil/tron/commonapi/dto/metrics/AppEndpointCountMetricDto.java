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
public class AppEndpointCountMetricDto {
    @Getter
    @Setter
    private UUID id;

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
    private String appSource;
    
    @Getter
    @Setter
    @Builder.Default
    @Valid
    private List<CountMetricDto> appClients = new ArrayList<>();  
}
