package mil.tron.commonapi.dto.jsondb;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
public class ChangeElementDto {

    @Getter
    @Setter
    private Object json;

    @Getter
    @Setter
    private String path;

}