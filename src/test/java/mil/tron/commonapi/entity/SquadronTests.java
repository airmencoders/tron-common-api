package mil.tron.commonapi.entity;

import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.Squadron;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
