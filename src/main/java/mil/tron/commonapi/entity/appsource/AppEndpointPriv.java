package mil.tron.commonapi.entity.appsource;

import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.validations.AppsMatch;

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
@AppsMatch(invert = true, field = "appSource", fieldMatch = "appClientUser", message="App cannot fetch from itself")
@Table(name="app_endpoint_privs")
public class AppEndpointPriv {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @ManyToOne
    @JoinColumn(name = "app_source_id", nullable = false)
    @EqualsAndHashCode.Exclude
    private AppSource appSource;

    @ManyToOne
    @JoinColumn(name = "app_endpoint_id", nullable = false)
    @EqualsAndHashCode.Exclude
    private AppEndpoint appEndpoint;

    @ManyToOne
    @EqualsAndHashCode.Exclude
    private AppClientUser appClientUser;
}
