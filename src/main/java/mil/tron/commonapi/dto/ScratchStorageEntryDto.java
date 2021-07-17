package mil.tron.commonapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Representation of a scratch storage key/value pair
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScratchStorageEntryDto {

    @JsonIgnore
    @Getter
    @Setter
    @Builder.Default
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id = UUID.randomUUID();

    @Getter
    @Setter
    private String value;
    
    @Getter
    @Setter
    @NotNull
    @NotBlank
    private String key;

    @Getter
    @Setter
    @NotNull
    private UUID appId;


}
