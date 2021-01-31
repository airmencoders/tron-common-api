package mil.tron.commonapi.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotBlank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
	
	private String nameAsLower;
	
	@Getter
	@Setter
	@Builder.Default
	@ManyToMany
	private Set<Privilege> privileges = new HashSet<>();
	
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
