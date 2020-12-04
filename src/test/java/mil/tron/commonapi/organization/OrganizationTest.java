package mil.tron.commonapi.organization;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.springframework.boot.test.context.SpringBootTest;

import mil.tron.commonapi.person.Person;

import java.util.UUID;

@SpringBootTest
public class OrganizationTest {

    @Test
    public void checkUniquenessOnID() {
        Organization testOrganization = new Organization();
        UUID firstUUID = testOrganization.getId();
        Organization secondTestPerson = new Organization();
        UUID secondUUID = secondTestPerson.getId();
        boolean areEqual = firstUUID == secondUUID;
        assertEquals(areEqual, false);
    }

    @Test
    public void canAddAndRemoveSubordinateOrganization() {
        Organization testOrg = new Organization();
        Organization subOrg = new Organization();
        testOrg.addSubordinateOrganization(subOrg);
        assertEquals(testOrg.getSubordinateOrganizations().contains(subOrg), true);
        assertEquals(testOrg.removeSubordinateOrganization(subOrg), true);
        assertEquals(testOrg.getSubordinateOrganizations().contains(subOrg), false);
        assertEquals(testOrg.removeSubordinateOrganization(subOrg), false);
        assertEquals(testOrg.removeSubordinateOrganization(subOrg), false);
    }

    @Test
    public void canAddAndRemoveMembers() {
        Organization testOrg = new Organization();
        Person testPerson = new Person();
        testOrg.addMember(testPerson);
        assertEquals(testOrg.getMembers().contains(testPerson), true);
        assertEquals(testOrg.removeMember(testPerson), true);
        assertEquals(testOrg.getMembers().contains(testPerson), false);
        assertEquals(testOrg.removeMember(testPerson), false);
    }

    @Test
    public void canAddAndRemoveLeaders() {
        Organization testOrg = new Organization();
        Person testPerson1 = new Person();
        Person testPerson2 = new Person();
        testOrg.setLeaderAndUpdateMembers(testPerson1);
        assertEquals(testOrg.getMembers().contains(testPerson1), true);
        assertEquals(testOrg.getLeader(), testPerson1);
        testOrg.setLeaderAndUpdateMembers(testPerson2);
        assertEquals(testOrg.getMembers().contains(testPerson1), false);
    }

    @Test
    public void testEquals() {
        Organization testOrg = new Organization();
        Organization testOrg2 = new Organization();
        Object testObj = new Object();
        assertEquals(testOrg.equals(null), false);
        assertEquals(testOrg.equals(testObj), false);
        assertEquals(testOrg.equals(testOrg), true);
        assertEquals(testOrg.equals(testOrg2), false);
    }
    
}
