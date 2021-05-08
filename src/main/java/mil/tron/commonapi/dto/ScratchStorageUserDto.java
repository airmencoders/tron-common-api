package mil.tron.commonapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Represents a user/consumer of the scratch space -- they will have a privilege associated with them
 *
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScratchStorageUserDto {

    @Getter
    @Setter
    @NotNull
    @NotBlank
    @Email(message = "Malformed email address")
    private String email;

    @Getter
    @Setter
    @Builder.Default
    private UUID id = UUID.randomUUID();
}
