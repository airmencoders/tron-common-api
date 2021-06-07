package mil.tron.commonapi.dto.appclient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import mil.tron.commonapi.dto.PrivilegeDto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A detailed POJO to show more information about a given App client
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppClientUserDetailsDto {

    @Getter
    @Setter
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Getter
    @Setter
    @Builder.Default
    private List<String> appClientDeveloperEmails = new ArrayList<>();

    @Getter
    @Setter
    @Builder.Default
    private List<PrivilegeDto> privileges = new ArrayList<>();

    @Getter
    @Setter
    @Builder.Default
    private List<AppEndpointClientInfoDto> appEndpointPrivs = new ArrayList<>();

    @Getter
    @Setter
    private String name;

    @Getter
    @Setter
    private String clusterUrl;
}
