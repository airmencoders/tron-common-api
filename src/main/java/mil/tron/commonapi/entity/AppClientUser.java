package mil.tron.commonapi.entity;

import lombok.*;
import lombok.experimental.SuperBuilder;
import mil.tron.commonapi.entity.appsource.App;
import mil.tron.commonapi.entity.appsource.AppEndpointPriv;
import mil.tron.commonapi.entity.documentspace.DocumentSpace;
import mil.tron.commonapi.entity.documentspace.DocumentSpacePrivilege;
import mil.tron.commonapi.validations.ValidSubscriberAddress;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Class that represents a Client User entity that is an Application
 *
 */
@Entity
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Table(name="app")
public class AppClientUser extends App {
	
	
    /**
     * This flag allows an App Client to be identified as an App Client in the App table
     */    
    @Getter
	@Setter
    @Builder.Default
    private boolean availableAsAppClient = true;

	@Getter
	@Setter
	@Builder.Default
	@ManyToMany(fetch = FetchType.EAGER)
	private Set<Privilege> privileges = new HashSet<>();

	/**
	 * List of App Client Developers that can see what app sources this client is connected to
	 * An app client can have many developers...
	 */
	@Getter
	@Setter
	@Builder.Default
	@ManyToMany
	private Set<DashboardUser> appClientDevelopers = new HashSet<>();

	@Getter
	@Setter
	@Builder.Default
	@OneToMany(fetch = FetchType.EAGER, mappedBy = "appClientUser")
	private Set<AppEndpointPriv> appEndpointPrivs = new HashSet<>();
	
	@Getter
    @Setter
    @Builder.Default
    @ManyToMany
    @JoinTable(
    		joinColumns=@JoinColumn(referencedColumnName="id"),
    		inverseJoinColumns=@JoinColumn(referencedColumnName="id")
		)
	@EqualsAndHashCode.Exclude
    private Set<DocumentSpacePrivilege> documentSpacePrivileges = new HashSet<>();
	
	@Getter
    @Setter
    @Builder.Default
    @ManyToMany(mappedBy="appClientUsers")
    @EqualsAndHashCode.Exclude
    private Set<DocumentSpace> documentSpaces = new HashSet<>();
    
    public void addDocumentSpacePrivilege(DocumentSpacePrivilege privilege) {
    	documentSpacePrivileges.add(privilege);
    	privilege.getAppClientUsers().add(this);
    }
    
    public void removeDocumentSpacePrivilege(DocumentSpacePrivilege privilege) {
    	documentSpacePrivileges.remove(privilege);
    	privilege.getAppClientUsers().remove(this);
    }
    
    public void addDocumentSpace(DocumentSpace documentSpace) {
    	documentSpaces.add(documentSpace);
    	documentSpace.getAppClientUsers().add(this);
    }
    
    public void removeDocumentSpace(DocumentSpace documentSpace) {
    	documentSpaces.remove(documentSpace);
    	documentSpace.getAppClientUsers().remove(this);
    }

	/**
	 * The P1 cluster (internal) URL of this application.
	 * Initialize it to its likely value.
	 */
	@Getter
	@Setter
	@ValidSubscriberAddress
	private String clusterUrl = String.format("http://%s.%s.svc.cluster.local/", super.getName(), super.getName());

}
