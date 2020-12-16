package mil.tron.commonapi.entity;

import mil.tron.commonapi.entity.Organization;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.springframework.boot.test.context.SpringBootTest;

import mil.tron.commonapi.entity.Person;

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
        assertEquals(true, testOrg.getSubordinateOrganizations().contains(subOrg));
        assertEquals(true, testOrg.removeSubordinateOrganization(subOrg));
        assertEquals(false, testOrg.getSubordinateOrganizations().contains(subOrg));
        assertEquals(false, testOrg.removeSubordinateOrganization(subOrg));
        assertEquals(false, testOrg.removeSubordinateOrganization(subOrg));
    }

    @Test
    public void canAddAndRemoveMembers() {
        Organization testOrg = new Organization();
        Person testPerson = new Person();
        testOrg.addMember(testPerson);
        assertEquals(true, testOrg.getMembers().contains(testPerson));
        assertEquals(true, testOrg.removeMember(testPerson));
        assertEquals(false, testOrg.getMembers().contains(testPerson));
        assertEquals(false, testOrg.removeMember(testPerson));
    }

    @Test
    public void canAddAndRemoveLeaders() {
        Organization testOrg = new Organization();
        Person testPerson1 = new Person();
        Person testPerson2 = new Person();
        testOrg.setLeaderAndUpdateMembers(testPerson1);
        assertEquals(true, testOrg.getMembers().contains(testPerson1));
        assertEquals(testPerson1, testOrg.getLeader());
        testOrg.setLeaderAndUpdateMembers(testPerson2);
        assertEquals(false, testOrg.getMembers().contains(testPerson1));
    }

    @Test
    public void testEquals() {
        Organization testOrg = new Organization();
        Organization testOrg2 = new Organization();
        Object testObj = new Object();
        assertEquals(false, testOrg.equals(null));
        assertEquals(false, testOrg.equals(testObj));
        assertEquals(true, testOrg.equals(testOrg));
        assertEquals(false, testOrg.equals(testOrg2));
    }
    
}
