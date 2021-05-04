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
import mil.tron.commonapi.dto.appsource.EndpointDto;

@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AppEndpointCountMetricDto extends EndpointDto {

    @Builder
    public AppEndpointCountMetricDto(UUID id, String path, String requestType, String appSource, List<CountMetricDto> appClients) {
        super(id, path, requestType);
        this.appSource = appSource;
        this.appClients = appClients;
    }
  
    @Getter
    @Setter
    private String appSource;
    
    @Getter
    @Setter
    @Valid
    private List<CountMetricDto> appClients = new ArrayList<>();  
}
