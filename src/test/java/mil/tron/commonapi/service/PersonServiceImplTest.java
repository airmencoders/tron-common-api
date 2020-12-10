package mil.tron.commonapi.service;

import mil.tron.commonapi.person.Person;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.UUID;

public class PersonServiceImplTest {

    @Test
    public void createPersonTest() {
        Person testPerson = new Person();
        PersonServiceImpl testService = new PersonServiceImpl();
        Person returnVal = testService.createPerson(testPerson);
        assertNotNull(returnVal);
    }

    @Test
    public void updatePersonTest() {
        Person testPerson = new Person();
        testPerson.setFirstName("");
        PersonServiceImpl testService = new PersonServiceImpl();
        Person firstReturnVal = testService.createPerson(testPerson);
        String firstReturnName = firstReturnVal.getFirstName();
        testPerson.setFirstName("Billy");
        Person secondReturnVal = testService.updatePerson(testPerson.getId(), testPerson);
        Person thirdReturnVal = testService.updatePerson(UUID.randomUUID(), testPerson);
        assertEquals(true, firstReturnVal.equals(secondReturnVal));
        assertEquals(false, firstReturnName.equals(secondReturnVal.getFirstName()));
        assertNull(thirdReturnVal);
    }

    @Test
    public void deletePersonTest() {
        Person testPerson = new Person();
        PersonServiceImpl testService = new PersonServiceImpl();
        testService.deletePerson(testPerson.getId());
    }

    @Test
    public void getPersonsTest() {
        Person testPerson = new Person();
        PersonServiceImpl testService = new PersonServiceImpl();
        testService.createPerson(testPerson);
        Collection<Person> people = testService.getPersons();
        assertTrue(people.contains(testPerson));
    }

    @Test
    public void getPersonTest() {
        Person testPerson = new Person();
        PersonServiceImpl testService = new PersonServiceImpl();
        testService.createPerson(testPerson);
        Person returnPerson = testService.getPerson(testPerson.getId());
        assertEquals(testPerson, returnPerson);
    }
    
}
