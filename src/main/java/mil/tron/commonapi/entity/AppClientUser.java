package mil.tron.commonapi.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mil.tron.commonapi.entity.appsource.AppSourcePriv;

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

	@Getter
	@Setter
	@Builder.Default
	@OneToMany(mappedBy = "appClientUser")
	private Set<AppSourcePriv> appSourcePrivs = new HashSet<>();
	
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
