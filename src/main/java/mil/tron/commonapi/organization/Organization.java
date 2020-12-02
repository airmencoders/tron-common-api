package mil.tron.commonapi.organization;

import lombok.*;

import javax.persistence.*;

import java.util.List;

import java.util.ArrayList;
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
    private List<Person> members = new ArrayList<Person>();

    @Getter
    @Setter
    private Person leader;
    
    @Getter
    @Setter
    private Organization parentOrganization;
    
    @Getter
    @Builder.Default
    private List<Organization> subordinateOrganizations = new ArrayList<Organization>();
    
    public boolean equals(Organization other) {
        if (other == null) {
            return false;
        }
        else {
            return this.id == other.getId();
        }
    }
    
    public void addSubordinateOrganization(@NonNull Organization subOrg) {
        if (!this.subordinateOrganizations.contains(subOrg)) {
            this.subordinateOrganizations.add(subOrg);
        }
    }
    
    public boolean removeSubordinateOrganization(@NonNull Organization subOrg) {
        if (this.subordinateOrganizations.remove(subOrg)) {
            return true;
        }
        else {
            return false;
        }
    }
    
    public void setLeaderAndUpdateMembers(@NonNull Person leader) {
        this.members.remove(this.leader);
        this.leader = leader;
        if (!this.members.contains(leader)) {
            this.members.add(leader);
        }
    }
    
    
    public void addMember(@NonNull Person member) {
        if (!this.members.contains(member)) {
            this.members.add(member);
        }
    }
    
    public boolean removeMember(@NonNull Person member) {
        if (this.members.remove(member)) {
            return true;
        }
        else {
            return false;
        }
    }
    
}