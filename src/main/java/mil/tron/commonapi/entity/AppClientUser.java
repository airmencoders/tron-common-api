package mil.tron.commonapi.entity;

import lombok.*;
import mil.tron.commonapi.entity.appsource.AppEndpointPriv;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Class that represents a Client User entity that is an Application
 *
 */
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name="app_client_user", uniqueConstraints = { @UniqueConstraint(columnNames = "nameAsLower", name = "appClientUser_nameAsLower_key") })
public class AppClientUser {
	@Id
    @Getter
    @Setter
    @Builder.Default
    private UUID id = UUID.randomUUID();
	
	@Getter
	@Setter
	@NotBlank
	private String name;
	
	@Getter
	private String nameAsLower;
	
	@Getter
	@Setter
	@Builder.Default
	@ManyToMany
	private Set<Privilege> privileges = new HashSet<>();

	/**
	 * List of App Client Developers that can see what app sources this client is connected to
	 * An app client can have many developers...
	 */
	@Getter
	@Setter
	@ManyToMany
	private Set<DashboardUser> appClientDevelopers = new HashSet<>();

	@Getter
	@Setter
	@Builder.Default
	@OneToMany(mappedBy = "appClientUser")
	private Set<AppEndpointPriv> appEndpointPrivs = new HashSet<>();
	
	@PrePersist 
	@PreUpdate 
	public void sanitize() {
		trimStringFields();
		sanitizeNameForUniqueConstraint();
	}
	
	private void sanitizeNameForUniqueConstraint() {
        nameAsLower = name == null ? null : name.toLowerCase();
    }
	
	private void trimStringFields() {
		name = name == null ? null : name.trim();
	}
}
