package mil.tron.commonapi.dto.appsource;

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
import mil.tron.commonapi.dto.AppClientUserPrivDto;
import mil.tron.commonapi.validations.AppAndIdMatch;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
@AppAndIdMatch
public class AppSourceDetailsDto {

    @Getter
    @Setter
    @Builder.Default
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
    private Integer clientCount = 0;
    
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
}
