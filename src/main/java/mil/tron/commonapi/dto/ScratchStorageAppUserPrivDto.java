package mil.tron.commonapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Used to set/define priv (by its Privilege id) to assign to a user (by user email)
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
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id = UUID.randomUUID();

    @Getter
    @NotBlank
    @NotNull
    @Email(message="Malformed email address")
    private String email;

    public void setEmail(String email) {
        this.email = email.trim().toLowerCase();
    }

    @Getter
    @Setter
    @NotNull
    private Long privilegeId;
}
