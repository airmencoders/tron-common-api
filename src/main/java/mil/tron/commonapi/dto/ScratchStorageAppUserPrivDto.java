package mil.tron.commonapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Used to set/define priv (by id) to assign to a user (by user id)
 * which will then be bound to a scratch space application entry
 * in the ScratchStorageAppRegistry table
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScratchStorageAppUserPrivDto {

    @Getter
    @Setter
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Getter
    @Setter
    @NotNull
    private UUID userId;

    @Getter
    @Setter
    @NotNull
    private Long privilegeId;
}
