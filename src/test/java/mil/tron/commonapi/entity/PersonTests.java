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
        testPerson.sanitizeEntity();
        assertEquals(testPerson.getEmail(), "test@test.com");
        assertEquals(testPerson.getFirstName(), "John");
        assertEquals(testPerson.getMiddleName(), "Middle");
        assertEquals(testPerson.getLastName(), "Doe");
        assertEquals(testPerson.getTitle(), "no one");
    }
}
