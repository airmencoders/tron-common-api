package mil.tron.commonapi.entity.appsource;

import lombok.*;
import mil.tron.commonapi.entity.DashboardUser;

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

    /**
     * List of AppSourceAdmins that can administer this app
     * One app can have many admins...
     */
    @Getter
    @Setter
    @ManyToMany
    private Set<DashboardUser> appSourceAdmins = new HashSet<>();

    @Getter
    @Setter
    @Builder.Default
    @OneToMany(mappedBy = "appSource")
    private Set<AppEndpoint> appEndpoints = new HashSet<>();

    @Getter
    @Setter
    @Builder.Default
    @OneToMany(mappedBy = "appSource")
    private Set<AppEndpointPriv> appPrivs = new HashSet<>();

    @PrePersist
    @PreUpdate
    public void sanitize() {
        trimStringFields();
    }

    private void trimStringFields() {
        name = name == null ? null : name.trim();
    }
}
