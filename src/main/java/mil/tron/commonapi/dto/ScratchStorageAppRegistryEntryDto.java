package mil.tron.commonapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import mil.tron.commonapi.entity.scratch.ScratchStorageAppUserPriv;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a scratch app's existence
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScratchStorageAppRegistryEntryDto {


    @Getter
    @Setter
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Getter
    @Setter
    private boolean appHasImplicitRead = false;

    @Getter
    @Builder.Default
    private Set<ScratchStorageAppUserPriv> userPrivs = new HashSet<>();

    @Getter
    @Setter
    @NotBlank
    @NotNull
    private String appName;
}
