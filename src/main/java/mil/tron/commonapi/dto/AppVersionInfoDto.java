package mil.tron.commonapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public class AppVersionInfoDto {

    @Getter
    @Setter
    private String version;

    @Getter
    @Setter
    private String enclave;

    @Getter
    @Setter
    private String environment;
}
