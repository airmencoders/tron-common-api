package mil.tron.commonapi.dto.appsource;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
public class AppSourceDto {

    @Getter
    @Setter
    @NotBlank
    @NotNull
    private String name;

    @Getter
    @Setter
    @Builder.Default
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id = UUID.randomUUID();

    @Getter
    @Setter
    @Builder.Default
    private Integer endpointCount = 0;

    @Getter
    @Setter
    @Builder.Default
    private Integer clientCount = 0;
}
