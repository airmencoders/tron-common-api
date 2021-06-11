package mil.tron.commonapi.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import javax.persistence.*;
import javax.validation.constraints.Email;
import java.util.HashSet;
import java.util.Set;
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
    @Column(unique = true, nullable = false)
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
    @Builder.Default
    @ManyToMany(fetch = FetchType.EAGER)
    private Set<Privilege> privileges = new HashSet<>();

    @PrePersist
    @PreUpdate
    public void sanitize() {
        trimStrings();
        sanitizeNameForUniqueConstraint();
    }

    private void sanitizeNameForUniqueConstraint() {
        emailAsLower = email == null ? null : email.toLowerCase();
    }

    private void trimStrings() {
        email = trim(email);
    }
    private final String trim(String value) {
        return value == null ? null : value.trim();
    }
}
