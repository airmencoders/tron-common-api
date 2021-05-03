package mil.tron.commonapi.dto.jsondb;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
public class QueryDto {

    @Getter
    @Setter
    private String tableName;

    @Getter
    @Setter
    private String query;

}
