package mil.tron.commonapi.dto.appsource;

import lombok.*;

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
    private String name;

    @Getter
    @Setter
    @Builder.Default
    private Integer clientCount = 0;
}
