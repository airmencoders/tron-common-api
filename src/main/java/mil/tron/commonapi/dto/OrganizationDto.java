package mil.tron.commonapi.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.*;
import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.orgtypes.Unit;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Mirror Organization entity but only shows UUIDs for nested Persons/Orgs
 */

@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
public class OrganizationDto {

    @Getter
    @Setter
    private UUID id = UUID.randomUUID();

    @Getter
    private UUID leader;

    @Getter
    private Set<UUID> members = new HashSet<>();

    @Getter
    private UUID parentOrganization;

    @Getter
    private Set<UUID> subordinateOrganizations = new HashSet<>();

    @Getter
    @Setter
    private String name;

    @Getter
    private Unit orgType = Unit.ORGANIZATION;

    @Getter
    private Branch branchType = Branch.OTHER;

    @JsonSetter("orgType")
    public void setOrgType(Unit type) { this.orgType = type; }

    @JsonSetter("branchType")
    public void setBranchType(Branch branch) { this.branchType = branch; }

    /**
     * Custom setter used by Jackson for leader field on JSON deserialization
     */
    @JsonSetter("leader")
    public void setLeaderUUID(UUID id) {
        this.leader = id;
    }

    /**
     * Custom setter used by Jackson for members[] field on JSON deserialization
     */
    @JsonSetter("members")
    public void setMembersUUID(Set<UUID> ids) {
        this.members = ids;
    }

    /**
     * Custom setter used by Jackson for subordinateOrganizations[] field on JSON deserialization
     */
    @JsonSetter("subordinateOrganizations")
    public void setSubOrgsUUID(Set<UUID> ids) {
        this.subordinateOrganizations = ids;
    }

    /**
     * Custom setter used by Jackson for parentOrganization field on JSON deserialization
     */
    @JsonSetter("parentOrganization")
    public void setParentOrganizationUUID(UUID id) {
        this.parentOrganization = id;
    }

    /**
     * Setter used by model mapper during conversions
     * @param org
     */
    public void setParentOrganization(Organization org) {
        if (org != null) {
            this.parentOrganization = org.getId();
        }
    }

    /**
     * Setter used by model mapper during conversions
     * @param p
     */
    public void setLeader(Person p) {
        if (p != null) {
            this.leader = p.getId();
        }
    }

    /**
     * Setter used by model mapper during conversions
     * @param people
     */
    public void setMembers(Set<Person> people) {
        if (people != null) {
            for (Person p : people) {
                this.members.add(p.getId());
            }
        }
    }

    /**
     * Setter used by model mapper during conversions
     * @param orgs
     */
    public void setSubordinateOrganizations(Set<Organization> orgs) {
        if (orgs != null) {
            for (Organization o : orgs) {
                this.subordinateOrganizations.add(o.getId());
            }
        }
    }
}
