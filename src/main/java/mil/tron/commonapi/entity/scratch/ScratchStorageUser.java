package mil.tron.commonapi.entity.scratch;


import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import javax.persistence.*;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.UUID;

/** Represents a user/consumer of the scratch space -- they will have a privilege associated with them
 *
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name="scratch_storage_user", uniqueConstraints = { @UniqueConstraint(columnNames = "emailAsLower") })
public class ScratchStorageUser {

    @Id
    @Getter
    @Setter
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Getter
    @Setter
    @NotNull
    @NotBlank
    @Email(message = "Malformed email address")
    private String email;

    @JsonIgnore
    private String emailAsLower;

    @PreUpdate
    @PrePersist
    public void sanitizeEntity() {
        trimStrings();
        sanitizeEmailForUniqueConstraint();
    }

    private void sanitizeEmailForUniqueConstraint() {
        emailAsLower = email == null ? null : email.toLowerCase();
    }

    private void trimStrings() {
        email = trim(email);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
