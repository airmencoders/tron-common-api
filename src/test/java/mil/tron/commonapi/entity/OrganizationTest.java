package mil.tron.commonapi.entity;

import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest
class OrganizationTest {

    @Test
    void checkUniquenessOnID() {
        Organization testOrganization = new Organization();
        UUID firstUUID = testOrganization.getId();
        Organization secondTestPerson = new Organization();
        UUID secondUUID = secondTestPerson.getId();
        boolean areEqual = firstUUID == secondUUID;
        assertEquals(areEqual, false);
    }

    @Test
    void canAddAndRemoveSubordinateOrganization() {
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
    void canAddAndRemoveMembers() {
        Organization testOrg = new Organization();
        Person testPerson = new Person();
        testOrg.addMember(testPerson);
        assertEquals(true, testOrg.getMembers().contains(testPerson));
        assertEquals(true, testOrg.removeMember(testPerson));
        assertEquals(false, testOrg.getMembers().contains(testPerson));
        assertEquals(false, testOrg.removeMember(testPerson));
    }

    @Test
    void canAddAndRemoveLeaders() {
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
    void canNullOutLeader() {
        // test if leader is nulled out (to no one), that previous guy is removed

        Organization testOrg = new Organization();
        Person testPerson1 = new Person();
        testOrg.setLeader(testPerson1);
        assertEquals(true, testOrg.getMembers().contains(testPerson1));
        testOrg.setLeader(null);
        assertFalse(testOrg.getMembers().contains(testPerson1));
        assertEquals(0, testOrg.getMembers().size());
    }

    @Test
    void testEquals() {
        Organization testOrg = new Organization();
        Organization testOrg2 = new Organization();
        Object testObj = new Object();
        assertEquals(false, testOrg.equals(null));
        assertEquals(false, testOrg.equals(testObj));
        assertEquals(true, testOrg.equals(testOrg));
        assertEquals(false, testOrg.equals(testOrg2));
    }
    
    @Test
    void testNameSanitization() {
    	Organization testOrg = new Organization();
    	
    	testOrg.setName("Test Org");
    	testOrg.sanitizeNameForUniqueConstraint();
    	assertThat(testOrg.getName()).isNotNull();
    	
    	testOrg.setName("");
    	testOrg.sanitizeNameForUniqueConstraint();
    	assertThat(testOrg.getName()).isNull();
    }

    @Test
    void testStringTrims() {
        Organization testOrg = new Organization();

        testOrg.setName(" TestOrg ");
        testOrg.sanitizeEntity();
        assertEquals(testOrg.getName(), "TestOrg");
    }

    @Test
    void testCantAddSelfAsParent() {
        Organization testOrg = new Organization();

        assertThrows(InvalidRecordUpdateRequest.class, () -> testOrg.setParentOrganization(testOrg));
    }

    @Test
    void testCantAddSelfAsSubordinate() {
        Organization testOrg = new Organization();

        assertThrows(InvalidRecordUpdateRequest.class, () -> testOrg.addSubordinateOrganization(testOrg));
    }
}
