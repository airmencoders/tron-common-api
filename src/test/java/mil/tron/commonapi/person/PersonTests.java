package mil.tron.commonapi.person;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest
public class PersonTests {

    @Test
    public void getFullName() {
        Person testPerson = new Person();
        testPerson.setFirstName("Dude");
        testPerson.setLastName("Duder");
        String actualFullName = testPerson.getFullName();
        String expectedFullName = "Dude Duder";
        assertEquals(actualFullName, expectedFullName);
    }

    @Test
    public void checkUniquenessOnID() {
        Person testPerson = new Person();
        UUID firstUUID = testPerson.getId();
        Person secondTestPerson = new Person();
        UUID secondUUID = secondTestPerson.getId();
        boolean areEqual = firstUUID == secondUUID;
        assertEquals(areEqual, false);
    }

    @Test
    public void shouldNotEqualNull() {
        Person testPerson = new Person();
        assertEquals(testPerson.equals(null), false);
        assertEquals(testPerson.equals(testPerson), true);
        assertEquals(testPerson.equals(new Object()), false);
        assertEquals(testPerson.equals(new Person()), false);
    }
}
