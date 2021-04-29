package mil.tron.commonapi.entity.appsource;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.Valid;

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
     * Currently. appSourcePath is not given on App Source creation. 
     * This flag allows an App Source to be identified as an App Source - removing the path requirement
     */    
    @Getter
    @Builder.Default
    private boolean appSource = true;

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

}
