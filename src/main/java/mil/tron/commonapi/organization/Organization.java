package mil.tron.commonapi.organization;

import lombok.*;

import javax.persistence.Entity;
import javax.persistence.*;

import java.util.ArrayList;
import java.util.UUID;

import mil.tron.commonapi.person.Person;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Organization {

    @Id
    @Getter
    @Builder.Default
    private UUID id = UUID.randomUUID();

    public boolean equals(Organization other) {
        return this.id == other.getId();
    }

    @Getter
    @Setter
    private String name;

    @Getter
    @Setter
    private Organization parentOrganization;

    @Getter
    @Builder.Default
    private ArrayList<Organization> subordinateOrganizations = new ArrayList<Organization>();

    public void addSubordinateOrganization(Organization subOrg) {
        if (!this.subordinateOrganizations.contains(subOrg)) {
            this.subordinateOrganizations.add(subOrg);
        }
    }

    public boolean removeSubordinateOrganization(Organization subOrg) {
        if (this.subordinateOrganizations.remove(subOrg)) {
            return true;
        }
        else {
            return false;
        }
    }

    @Getter
    private Person leader;

    public void setLeader(Person leader) {
        this.members.remove(this.leader);
        this.leader = leader;
        if (!this.members.contains(leader)) {
            this.members.add(leader);
        }
    }

    @Getter
    @Builder.Default
    private ArrayList<Person> members = new ArrayList<Person>();

    public void addMember(Person member) {
        if (!this.members.contains(member)) {
            this.members.add(member);
        }
    }

    public boolean removeMember(Person member) {
        if (this.members.remove(member)) {
            return true;
        }
        else {
            return false;
        }
    }

}
