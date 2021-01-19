package mil.tron.commonapi.entity;

import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.Squadron;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class SquadronTests {

    @Test
    public void squadronObjTests() {
        Person c = new Person();
        c.setEmail("chief@unit.com");
        Person d = new Person();
        d.setEmail("dude@unit.com");

        Squadron s = new Squadron();
        s.setBaseName("Travis AFB");
        s.setChief(c);
        s.setOperationsDirector(d);
        s.setMajorCommand("ACC");
        assertEquals("chief@unit.com", s.getChief().getEmail());
        assertEquals("dude@unit.com", s.getOperationsDirector().getEmail());
        assertEquals("Travis AFB", s.getBaseName());
        assertEquals("ACC", s.getMajorCommand());
    }

    @Test
    void testStringTrims() {
        Squadron testSquadron = new Squadron();

        testSquadron.setBaseName(" Test AFB ");
        testSquadron.setMajorCommand(" Test Command AFB ");
        testSquadron.sanitizeEntity();
        assertEquals(testSquadron.getBaseName(), "Test AFB");
        assertEquals(testSquadron.getMajorCommand(), "Test Command AFB");
    }
}
