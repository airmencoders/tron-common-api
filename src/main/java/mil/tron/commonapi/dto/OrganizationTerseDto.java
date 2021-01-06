package mil.tron.commonapi.dto;

import lombok.*;
import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.entity.Person;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Mirror Organization entity but only shows UUIDs for nested Persons/Orgs
 */

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
public class OrganizationTerseDto {

    private UUID id;
    private UUID leader;
    private Set<UUID> members = new HashSet<>();
    private UUID parentOrganization;
    private Set<UUID> subordinateOrganizations = new HashSet<>();
    private String name;

    public void setParentOrganization(Organization org) {
        if (org != null) {
            this.parentOrganization = org.getId();
        }
    }

    public void setLeader(Person p) {
        if (p != null) {
            this.leader = p.getId();
        }
    }

    public void setMembers(Set<Person> people) {
        if (people != null) {
            for (Person p : people) {
                this.members.add(p.getId());
            }
        }
    }

    public void setSubordinateOrganizations(Set<Organization> orgs) {
        if (orgs != null) {
            for (Organization o : orgs) {
                this.subordinateOrganizations.add(o.getId());
            }
        }
    }
}
