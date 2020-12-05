package mil.tron.commonapi.organization;

import lombok.*;

import javax.persistence.*;

import java.util.Set;

import java.util.HashSet;
import java.util.UUID;

import mil.tron.commonapi.person.Person;

@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Organization {

    @Id
    @Getter
    @Builder.Default
    private UUID id = UUID.randomUUID();
    
    
    @Getter
    @Setter
    private String name;

    @Getter
    @Builder.Default
    private Set<Person> members = new HashSet<Person>();

    @Getter
    @Setter
    private Person leader;
    
    @Getter
    @Setter
    private Organization parentOrganization;
    
    @Getter
    @Builder.Default
    private Set<Organization> subordinateOrganizations = new HashSet<Organization>();
 
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