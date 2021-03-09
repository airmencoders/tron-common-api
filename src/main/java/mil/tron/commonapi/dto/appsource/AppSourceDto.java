package mil.tron.commonapi.dto.appsource;

import lombok.*;
import mil.tron.commonapi.dto.AppClientUserPrivDto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
public class AppSourceDto {

    @Builder.Default
    private UUID id = UUID.randomUUID();

    private String name;

    @Builder.Default
    private Integer clientCount = 0;
}
