package mil.tron.commonapi.entity.documentspace;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.ManyToMany;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.service.documentspace.DocumentSpacePrivilegeType;

@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class DocumentSpacePrivilege {
	@Id
    @Builder.Default
    private UUID id = UUID.randomUUID();
	
	@Column(unique=true)
	private String name;
	
	@Enumerated(EnumType.STRING)
	private DocumentSpacePrivilegeType type;
	
	@ManyToMany(mappedBy="documentSpacePrivileges")
	@Builder.Default
	@EqualsAndHashCode.Exclude
	private Set<DashboardUser> dashboardUsers = new HashSet<>();
	
	@ManyToMany(mappedBy="documentSpacePrivileges")
	@Builder.Default
	@EqualsAndHashCode.Exclude
	private Set<AppClientUser> appClientUsers = new HashSet<>();
	
	public void addDashboardUser(DashboardUser dashboardUser) {
		dashboardUsers.add(dashboardUser);
		dashboardUser.getDocumentSpacePrivileges().add(this);
    }
    
    public void removeDashboardUser(DashboardUser dashboardUser) {
    	dashboardUsers.remove(dashboardUser);
    	dashboardUser.getDocumentSpacePrivileges().remove(this);
    }
    
    public void addAppClientUser(AppClientUser appClientUser) {
    	appClientUsers.add(appClientUser);
    	appClientUser.getDocumentSpacePrivileges().add(this);
    }
    
    public void removeAppClientUser(AppClientUser appClientUser) {
    	appClientUsers.remove(appClientUser);
    	appClientUser.getDocumentSpacePrivileges().remove(this);
    }
}
