package mil.tron.commonapi.dto.appsource;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import mil.tron.commonapi.dto.AppClientUserPrivDto;
import mil.tron.commonapi.validations.AppAndIdMatch;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
@AppAndIdMatch
public class AppSourceDetailsDto {

    @Getter
    @Setter
    @Builder.Default
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id = UUID.randomUUID();

    @Getter
    @Setter
    @NotBlank
    @NotNull
    private String name;

    @Getter
    @Setter
    private String appSourcePath;
    
    @Getter
    @Setter
    @Builder.Default
    private Integer endpointCount = 0;

    @Getter
    @Setter
    @Builder.Default
    private boolean reportStatus = false;

    @Getter
    @Setter
    @Builder.Default
    private Integer clientCount = 0;

    @Getter
    @Setter
    private String healthUrl;
    
    @Getter
    @Setter
    @Builder.Default
    @Valid
    private List<String> appSourceAdminUserEmails = new ArrayList<>();

    @Getter
    @Setter
    @Builder.Default
    @Valid
    private List<AppClientUserPrivDto> appClients = new ArrayList<>();

    @Getter
    @Setter
    @Builder.Default
    @Valid
    private List<AppEndpointDto> endpoints = new ArrayList<>();
    
    @Getter
    @Setter
    @Builder.Default
    private boolean throttleEnabled = false;
    
    @Getter
    @Setter
    @Min(0L)
    @Builder.Default
    private Long throttleRequestCount = 0L;
}
