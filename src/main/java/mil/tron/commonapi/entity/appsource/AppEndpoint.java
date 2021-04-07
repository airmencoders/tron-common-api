package mil.tron.commonapi.entity.appsource;

import lombok.*;

import javax.persistence.*;

import org.springframework.web.bind.annotation.RequestMethod;

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
@Table(name="app_endpoint", uniqueConstraints = { @UniqueConstraint(columnNames = {"path", "method", "app_source_id"})})
public class AppEndpoint {

    @Id
    @Getter
    @Setter
    @Builder.Default
    private UUID id = UUID.randomUUID();
    
    @Getter
    @Setter
    String path;

    @Getter
    @Setter
    RequestMethod method;

    @Getter
    @Setter
    @ManyToOne
    @JoinColumn(name = "app_source_id", nullable = false)
    @EqualsAndHashCode.Exclude
    private AppSource appSource;

    @Getter
    @Setter
    @Builder.Default
    @OneToMany(mappedBy = "appEndpoint")
    @EqualsAndHashCode.Exclude
    private Set<AppEndpointPriv> appEndpointPrivs = new HashSet<>();
}
