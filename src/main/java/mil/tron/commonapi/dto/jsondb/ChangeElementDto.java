package mil.tron.commonapi.dto.jsondb;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
public class ChangeElementDto {

    @Getter
    @Setter
    private String json;

    @Getter
    @Setter
    private String path;

}