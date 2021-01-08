package mil.tron.commonapi.dto;

import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.entity.Person;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OrganizationDtoTest {

    @Test
    void checkDtoFields() {
        Person p = new Person();
        Organization o = new Organization();

        // just check the fields we had to manually make a setter for
        OrganizationDto dto = new OrganizationDto();
        dto.setLeader(p);
        dto.setMembers(Set.of(p));
        dto.setParentOrganization(o);
        dto.setSubordinateOrganizations(Set.of(o));

        assertEquals(p.getId(), dto.getLeader());
        assertEquals(Set.of(p.getId()), dto.getMembers());
        assertEquals(Set.of(o.getId()), dto.getSubordinateOrganizations());
        assertEquals(o.getId(), dto.getParentOrganization());

    }
}
