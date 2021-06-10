package mil.tron.commonapi.dto;

import lombok.*;

import java.util.List;

/**
 * Simple DTO to convey fields present in the Person and Org fields
 * that are protected
 */

@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
public class EntityFieldsDto {

    @Getter
    @Setter
    private List<String> fields;
}
