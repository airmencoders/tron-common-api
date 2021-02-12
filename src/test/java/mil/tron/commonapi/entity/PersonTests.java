package mil.tron.commonapi.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

class PersonTests {

    @Test
    void getFullName() {
        Person testPerson = new Person();
        testPerson.setFirstName("Dude");
        testPerson.setLastName("Duder");
        String actualFullName = testPerson.getFullName();
        String expectedFullName = "Dude Duder";
        assertEquals(expectedFullName, actualFullName);
    }

    @Test
    void checkUniquenessOnID() {
        Person testPerson = new Person();
        UUID firstUUID = testPerson.getId();
        Person secondTestPerson = new Person();
        UUID secondUUID = secondTestPerson.getId();
        boolean areEqual = firstUUID == secondUUID;
        assertEquals(false, areEqual);
    }

    @Test
    void shouldNotEqualNull() {
        Person testPerson = new Person();
        assertEquals(false, testPerson.equals(null));
        assertEquals(true, testPerson.equals(testPerson));
        assertEquals(false, testPerson.equals(new Object()));
        assertEquals(false, testPerson.equals(new Person()));
    }
    
    @Test
    void testEmptyAndBlankEmails() {
    	Person testPerson = new Person();
    	
    	// Test empty string
    	testPerson.setEmail("");
    	testPerson.sanitizeEntity();
    	assertThat(testPerson.getEmail()).isNull();
    	
    	// Test one whitespace
    	testPerson.setEmail(" ");
    	testPerson.sanitizeEntity();
    	assertThat(testPerson.getEmail()).isNull();
    	
    	// Test multiple whitespace
    	testPerson.setEmail("     ");
    	testPerson.sanitizeEntity();
    	assertThat(testPerson.getEmail()).isNull();
    }

    @Test
    void testStringTrims() {
        Person testPerson = new Person();

        testPerson.setEmail(" test@test.com ");
        testPerson.setFirstName(" John ");
        testPerson.setMiddleName(" Middle ");
        testPerson.setLastName(" Doe ");
        testPerson.setTitle(" no one ");
        testPerson.setAfsc(" 92T1 ");
        testPerson.setDodid(" 0123456789 ");
        testPerson.setImds(" 00-20-3 ");
        testPerson.setUnit(" Test Unit ");
        testPerson.setWing(" Test Wing ");
        testPerson.setGp(" Test Group ");
        testPerson.setSquadron(" Test Squadron ");
        testPerson.setWc(" Test Wing Commander ");
        testPerson.setGo81(" Test Go81? ");
        testPerson.setDeros(" 2022-01-01 ");
        testPerson.setPhone(" (808)123-4567 ");
        testPerson.setAddress(" 1234 Test Street ");
        testPerson.setFltChief(" Test Flight Chief ");
        testPerson.setManNumber(" Test Man Number ");
        testPerson.setDutyTitle(" Test Duty Title ");
        testPerson.sanitizeEntity();
        assertEquals(testPerson.getEmail(), "test@test.com");
        assertEquals(testPerson.getFirstName(), "John");
        assertEquals(testPerson.getMiddleName(), "Middle");
        assertEquals(testPerson.getLastName(), "Doe");
        assertEquals(testPerson.getTitle(), "no one");
        assertEquals(testPerson.getAfsc(), "92T1");
        assertEquals(testPerson.getDodid(), "0123456789");
        assertEquals(testPerson.getImds(), "00-20-3");
        assertEquals(testPerson.getUnit(), "Test Unit");
        assertEquals(testPerson.getWing(), "Test Wing");
        assertEquals(testPerson.getGp(), "Test Group");
        assertEquals(testPerson.getSquadron(), "Test Squadron");
        assertEquals(testPerson.getWc(), "Test Wing Commander");
        assertEquals(testPerson.getGo81(), "Test Go81?");
        assertEquals(testPerson.getDeros(), "2022-01-01");
        assertEquals(testPerson.getPhone(), "(808)123-4567");
        assertEquals(testPerson.getAddress(), "1234 Test Street");
        assertEquals(testPerson.getFltChief(), "Test Flight Chief");
        assertEquals(testPerson.getManNumber(), "Test Man Number");
        assertEquals(testPerson.getDutyTitle(), "Test Duty Title");
    }
}
