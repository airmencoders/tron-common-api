package mil.tron.commonapi.entity.appsource;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import lombok.experimental.SuperBuilder;
import mil.tron.commonapi.entity.DashboardUser;

import javax.persistence.*;
import javax.validation.Valid;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

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

    /**
     * Last time this App Source received an "UP" status on its health
     */
    @Getter
    @Setter
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "UTC")
    private Date lastUpTime;

}
