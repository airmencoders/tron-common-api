package mil.tron.commonapi.entity.appsource;

import lombok.*;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/***
 * An App Source is an app which provides data to app clients
 */
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@Table(name="app_source", uniqueConstraints = { @UniqueConstraint(columnNames = "name")})
public class AppSource {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    private String name;

    @Getter
    @Setter
    @Builder.Default
    @OneToMany(mappedBy = "appSource")
    private Set<AppSourcePriv> appSourcePrivs = new HashSet<>();

    @PrePersist
    @PreUpdate
    public void sanitize() {
        trimStringFields();
        sanitizeNameForUniqueConstraint();
    }



    private void sanitizeNameForUniqueConstraint() {
        name = name == null ? null : name.toLowerCase();
    }

    private void trimStringFields() {
        name = name == null ? null : name.trim();
    }
}
