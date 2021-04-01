package mil.tron.commonapi.dto.appsource;

import lombok.*;
import mil.tron.commonapi.dto.AppClientUserPrivDto;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
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
