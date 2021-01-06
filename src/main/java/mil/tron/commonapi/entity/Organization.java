package mil.tron.commonapi.entity;

import com.fasterxml.jackson.annotation.*;
import lombok.*;
import javax.persistence.*;
import javax.validation.constraints.NotBlank;

import java.util.Set;

import java.util.HashSet;
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
    private Set<Person> members = new HashSet<Person>();

    @Getter
    @Setter
    @ManyToOne
    private Person leader;

    @Getter
    @Setter
    @ManyToOne
    private Organization parentOrganization;

    @Getter
    @Builder.Default
    @OneToMany
    @JsonIgnore
    private Set<Organization> subordinateOrganizations = new HashSet<Organization>();

    /**
     * This method will be performed before database operations.
     *
     * Converts {@link Organization#name} to lowercase and sets it
     * to {@link Organization#nameAsLower}. This is needed for the
     * unique constraint in the database.
     */
    @PreUpdate
    @PrePersist
    public void sanitizeNameForUniqueConstraint() {
        if (name != null && name.isBlank()) {
            this.name = null;
        }

        nameAsLower = name == null ? null : name.toLowerCase();
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

    public void addSubordinateOrganization(Organization subOrg) {
        this.subordinateOrganizations.add(subOrg);
    }

    public boolean removeSubordinateOrganization(Organization subOrg) {
        return this.subordinateOrganizations.remove(subOrg);
    }

    @JsonIgnore
    public void setLeaderAndUpdateMembers(Person leader) {
        this.members.remove(this.leader);
        this.leader = leader;
        this.members.add(leader);
    }


    public void addMember(Person member) {
        this.members.add(member);
    }

    public boolean removeMember(Person member) {
        return this.members.remove(member);
    }

}