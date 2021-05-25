package mil.tron.commonapi.dto.appsource;

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
    private Integer endpointCount = 0;

    @Getter
    @Setter
    @Builder.Default
    private Integer clientCount = 0;
}
