package mil.tron.commonapi.entity;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import lombok.*;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.orgtypes.Unit;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@JsonIdentityInfo(generator= ObjectIdGenerators.PropertyGenerator.class, property="id")
@Table(uniqueConstraints = { @UniqueConstraint(columnNames = "nameAsLower") })
public class Organization {

    @Id
    @Getter
    @Setter
    private UUID id = UUID.randomUUID();

    @Getter
    @Setter
    @NotBlank
    private String name;
    
    /**
     * Converted value of {@link Organization#name} to lowercase. 
     * This is used for a unique constraint in the 
     * database for {@link Organization#name}.
     */
    @JsonIgnore
    private String nameAsLower;

    @Getter
    @Builder.Default
    @ManyToMany
    @JoinTable(name = "organization_members", 
        joinColumns = @JoinColumn(name = "organization_id"),
        inverseJoinColumns = @JoinColumn(name = "members_id"))
    private Set<Person> members = new HashSet<>();

    public void setMembers(Set<Person> members) {
        this.members = members;
    }

    @Getter
    @ManyToOne
    private Person leader;

    @Getter
    @ManyToOne
    private Organization parentOrganization;

    @Getter
    @Builder.Default
    @OneToMany
    @JsonIgnore
    private Set<Organization> subordinateOrganizations = new HashSet<>();

    @Getter
    @Setter
    @Builder.Default
    @Enumerated(value = EnumType.STRING)
    private Unit orgType = Unit.ORGANIZATION;

    @Getter
    @Setter
    @Builder.Default
    @Enumerated(value = EnumType.STRING)
    protected Branch branchType = Branch.OTHER;

    @Getter
    @Builder.Default
    @OneToMany(fetch = FetchType.EAGER)
    @JoinColumn(name="organizationId")
    private Set<OrganizationMetadata> metadata = new HashSet<>();

    /**
     * Custom setter for parent organization, checks to make sure we're not setting an org's parent as itself
     * @param parent Organization to add as the parent entity
     */
    public void setParentOrganization(Organization parent) {
        if (parent != null && parent.getId().equals(this.getId())) {
            throw new InvalidRecordUpdateRequest("An organization cannot add itself as its parent");
        }
        this.parentOrganization = parent;
    }

    /**
     * Custom setter for leader, forces leader to be a member if not already in the
     * organization they're leading.  Also removes the current leader from the org's members.
     * @param leader Person entity of incoming leader
     */
    public void setLeader(Person leader) {
        this.setLeaderAndUpdateMembers(leader);
    }


    @CreationTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "date_created")
    private Date dateCreated;

    @UpdateTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "date_modified")
    private Date dateModified;

    /**
     * This method will be performed before database operations.
     *
     * Entity parameters are formatted as needed
     */
    @PreUpdate
    @PrePersist
    public void sanitizeEntity() {
        trimStrings();
        sanitizeNameForUniqueConstraint();
    }
    /**
     * This method will be performed before database operations.
     * 
     * Converts {@link Organization#name} to lowercase and sets it
     * to {@link Organization#nameAsLower}. This is needed for the
     * unique constraint in the database.
     */
    public void sanitizeNameForUniqueConstraint() {
        if (name != null && name.isBlank()) {
            this.name = null;
        }

        nameAsLower = name == null ? null : name.toLowerCase();
    }

    public void trimStrings() {
        name = name.trim();
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Organization) {
            Organization otherOrg = (Organization) other;
            return this.id == otherOrg.getId();
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

    public void addSubordinateOrganization(Organization subOrg) {
        if (subOrg.getId().equals(this.getId())) {
            throw new InvalidRecordUpdateRequest("An organization cannot add itself as a subordinate");
        }
        this.subordinateOrganizations.add(subOrg);
    }

    public boolean removeSubordinateOrganization(Organization subOrg) {
        return this.subordinateOrganizations.remove(subOrg);
    }

    @JsonIgnore
    public void setLeaderAndUpdateMembers(Person leader) {
        this.members.remove(this.leader);
        this.leader = leader;
        if (leader != null) {
            this.members.add(leader);
        }
    }


    public void addMember(Person member) {
        this.members.add(member);
    }

    public boolean removeMember(Person member) {
        return this.members.remove(member);
    }

}