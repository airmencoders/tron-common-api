package mil.tron.commonapi.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import javax.persistence.*;
import javax.validation.constraints.Email;
import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name="dashboard_user", uniqueConstraints = { @UniqueConstraint(columnNames = "emailAsLower", name = "dashboardUser_emailAsLower_key") })
public class DashboardUser {
    @Id
    @Getter
    @Setter
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Email(message="Malformed email address")
    @Getter
    @Setter
    private String email;

    /**
     * Converted value of {@link DashboardUser#email} to lowercase.
     * This is used for a unique constraint in the database for emails.
     */
    @JsonIgnore
    private String emailAsLower;

    @Getter
    @Setter
    private boolean isAdmin;

    @PrePersist
    @PreUpdate
    public void sanitize() {
        trimStringFields();
        sanitizeNameForUniqueConstraint();
    }

    private void sanitizeNameForUniqueConstraint() {
        emailAsLower = email == null ? null : email.toLowerCase();
    }

    private void trimStringFields() {
        email = email == null ? null : email.trim();
    }
}
