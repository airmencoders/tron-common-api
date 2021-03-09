package mil.tron.commonapi.dto.appsource;

import lombok.*;
import mil.tron.commonapi.dto.AppClientUserPrivDto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
public class AppSourceDetailsDto {

    @Builder.Default
    private UUID id = UUID.randomUUID();

    @NotBlank
    @NotNull
    private String name;

    @Builder.Default
    private List<AppClientUserPrivDto> appClients = new ArrayList<>();
}
