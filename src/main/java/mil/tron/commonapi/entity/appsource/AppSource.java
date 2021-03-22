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
@Builder
@EqualsAndHashCode
@Table(name="app_source")
public class AppSource {

    @Id
    @Getter
    @Setter
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Getter
    @Setter
    @Column(unique = true)
    private String name;

    @Getter
    @Setter
    private String openApiSpecFilename;

    @Getter
    @Setter
    @Column(unique = true)
    private String appSourcePath;

    @Getter
    @Setter
    @Builder.Default
    @OneToMany(mappedBy = "appSource")
    private Set<AppSourcePriv> appSourcePrivs = new HashSet<>();

    @PrePersist
    @PreUpdate
    public void sanitize() {
        trimStringFields();
    }

    private void trimStringFields() {
        name = name == null ? null : name.trim();
    }
}
