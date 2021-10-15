package mil.tron.commonapi.entity.documentspace;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.EnumMap;
import java.util.HashSet;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.service.documentspace.DocumentSpacePrivilegeType;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public class DocumentSpace {
	@Id
	@Builder.Default
	private UUID id = UUID.randomUUID();
	
	@NotBlank
	@NotNull
	@Size(max = 255)
	@Column(unique = true)
	private String name;
	
	@OneToMany(mappedBy="documentSpace")
	@MapKeyEnumerated(EnumType.STRING)
	@MapKey(name="type")
	@Builder.Default
	@EqualsAndHashCode.Exclude
	private Map<DocumentSpacePrivilegeType, DocumentSpacePrivilege> privileges = new EnumMap<>(DocumentSpacePrivilegeType.class);
	
	@ManyToMany
	@JoinTable(
			name="document_space_dashboard_users",
    		joinColumns=@JoinColumn(name="document_space_id", referencedColumnName="id"),
    		inverseJoinColumns=@JoinColumn(name="dashboard_user_id", referencedColumnName="id")
		)
	@Builder.Default
	@EqualsAndHashCode.Exclude
	private Set<DashboardUser> dashboardUsers = new HashSet<>();
	
	@ManyToMany
	@JoinTable(
			name="document_space_app_users",
    		joinColumns=@JoinColumn(name="document_space_id", referencedColumnName="id"),
    		inverseJoinColumns=@JoinColumn(name="app_id", referencedColumnName="id")
		)
	@Builder.Default
	@EqualsAndHashCode.Exclude
	private Set<AppClientUser> appClientUsers = new HashSet<>();
	
	public void addDashboardUser(DashboardUser dashboardUser) {
		dashboardUsers.add(dashboardUser);
		dashboardUser.getDocumentSpaces().add(this);
    }
    
    public void removeDashboardUser(DashboardUser dashboardUser) {
    	dashboardUsers.remove(dashboardUser);
    	dashboardUser.getDocumentSpaces().remove(this);
    }
    
    public void addAppClientUser(AppClientUser appClientUser) {
    	appClientUsers.add(appClientUser);
    	appClientUser.getDocumentSpaces().add(this);
    }
    
    public void removeAppClientUser(AppClientUser appClientUser) {
    	appClientUsers.remove(appClientUser);
    	appClientUser.getDocumentSpaces().remove(this);
    }

	@Builder.Default
	@Getter
	@OneToMany(fetch = FetchType.EAGER)
	@JoinColumn(name="doc_space_id", updatable = false, insertable = false)
	private Set<DocumentSpaceFileSystemEntry> fileSystemEntries = new HashSet<>();

	public boolean addFileSystemEntry(DocumentSpaceFileSystemEntry entry) {
		return fileSystemEntries.add(entry);
	}
    
    public void addPrivilege(DocumentSpacePrivilege privilege) {
    	privileges.put(privilege.getType(), privilege);
    	privilege.setDocumentSpace(this);
    }
    
    public void removePrivilege(DocumentSpacePrivilege privilege) {
    	privileges.remove(privilege.getType());
    	privilege.setDocumentSpace(null);
    }
}
