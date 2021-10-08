package mil.tron.commonapi.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import mil.tron.commonapi.entity.documentspace.DocumentSpace;
import mil.tron.commonapi.entity.documentspace.DocumentSpacePrivilege;

import javax.persistence.*;
import javax.validation.constraints.Email;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name="dashboard_user", uniqueConstraints = { @UniqueConstraint(columnNames = "emailAsLower", name = "dashboardUser_emailAsLower_key") })
public class DashboardUser {
    @Id
    @Getter
    @Setter
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Email(message="Malformed email address")
    @Column(unique = true, nullable = false)
    @Getter
    @Setter
    private String email;

    /**
     * Converted value of {@link DashboardUser#email} to lowercase.
     * This is used for a unique constraint in the database for emails.
     */
    @JsonIgnore
    private String emailAsLower;

    @Getter
    @Setter
    @Builder.Default
    @ManyToMany(fetch = FetchType.EAGER)
    private Set<Privilege> privileges = new HashSet<>();

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
    @ManyToMany(mappedBy="dashboardUsers")
    @EqualsAndHashCode.Exclude
    private Set<DocumentSpace> documentSpaces = new HashSet<>();
    
    public void addDocumentSpacePrivilege(DocumentSpacePrivilege privilege) {
    	documentSpacePrivileges.add(privilege);
    	privilege.getDashboardUsers().add(this);
    }
    
    public void removeDocumentSpacePrivilege(DocumentSpacePrivilege privilege) {
    	documentSpacePrivileges.remove(privilege);
    	privilege.getDashboardUsers().remove(this);
    }
    
    public void addDocumentSpace(DocumentSpace documentSpace) {
    	documentSpaces.add(documentSpace);
    	documentSpace.getDashboardUsers().add(this);
    }
    
    public void removeDocumentSpace(DocumentSpace documentSpace) {
    	documentSpaces.remove(documentSpace);
    	documentSpace.getDashboardUsers().remove(this);
    }
    
    public void addPrivilege(Privilege privilege) {
    	privileges.add(privilege);
    }
    
    public void removePrivilege(Privilege privilege) {
    	privileges.remove(privilege);
    }
    
    @PrePersist
    @PreUpdate
    public void sanitize() {
        trimStrings();
        sanitizeNameForUniqueConstraint();
    }

    private void sanitizeNameForUniqueConstraint() {
        emailAsLower = email == null ? null : email.toLowerCase();
    }

    private void trimStrings() {
        email = trim(email);
    }
    private final String trim(String value) {
        return value == null ? null : value.trim();
    }
}
