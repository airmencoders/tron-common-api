package mil.tron.commonapi.entity.appsource;

import lombok.*;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.Privilege;

import javax.persistence.*;
import java.util.Set;
import java.util.UUID;

/**
 * Provides the privileges for a given app and client app
 */
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@Builder
@Entity
@Getter
@Setter
@Table(name="app_source_privs")
public class AppSourcePriv {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @ManyToOne
    @JoinColumn(name = "app_source_id", nullable = false)
    private AppSource appSource;

    @ManyToMany
    private Set<Privilege> privileges;

    @ManyToOne
    private AppClientUser appClientUser;
}
