package mil.tron.commonapi.entity.appsource;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import mil.tron.commonapi.entity.DashboardUser;

/***
 * An App Source is an app which provides data to app clients
 */
@Entity
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Table(name="app")
public class AppSource extends App {

    @Getter
    @Setter
    private String openApiSpecFilename;

    @Getter
    @Setter
    @Column(unique = true)
    private String appSourcePath;

    /**
     * This flag allows an App Source to be identified as an App Source in the App table
     */    
    @Getter
    @Setter
    @Builder.Default
    private boolean availableAsAppSource = true;

    /**
     * List of AppSourceAdmins that can administer this app
     * One app can have many admins...
     */
    @Getter
    @Setter
    @Builder.Default
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
    @Valid
    @OneToMany(mappedBy = "appSource")
    private Set<@Valid AppEndpointPriv> appPrivs = new HashSet<>();

    /**
     * Whether this app source wants to report up/down status
     */
    @Getter
    @Setter
    private boolean reportStatus = false;

    /**
     * The URL Common API should use to determine if this app is UP or DOWN
     * This will be a GET Request
     */
    @Getter
    @Setter
    private String healthUrl;

    @Getter
    @Setter
    @Builder.Default
    @NotNull
    private boolean throttleEnabled = false;
    
    /**
     * Rate Limit for the App Source
     */
    @Getter
    @Setter
    @Builder.Default
    @NotNull
    private long throttleRequestCount = 0L;
}
