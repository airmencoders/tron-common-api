package mil.tron.commonapi.person;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

public class PersonTests {

    @Test
    public void getFullName() {
        Person testPerson = new Person();
        testPerson.setFirstName("Dude");
        testPerson.setLastName("Duder");
        String actualFullName = testPerson.getFullName();
        String expectedFullName = "Dude Duder";
        assertEquals(expectedFullName, actualFullName);
    }

    @Test
    public void checkUniquenessOnID() {
        Person testPerson = new Person();
        UUID firstUUID = testPerson.getId();
        Person secondTestPerson = new Person();
        UUID secondUUID = secondTestPerson.getId();
        boolean areEqual = firstUUID == secondUUID;
        assertEquals(false, areEqual);
    }

    @Test
    public void shouldNotEqualNull() {
        Person testPerson = new Person();
        assertEquals(false, testPerson.equals(null));
        assertEquals(true, testPerson.equals(testPerson));
        assertEquals(false, testPerson.equals(new Object()));
        assertEquals(false, testPerson.equals(new Person()));
    }
}
