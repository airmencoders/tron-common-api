package mil.tron.commonapi.dto.appsource;

import java.util.UUID;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
public class AppEndpointDto {
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
}
