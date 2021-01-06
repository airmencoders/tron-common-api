package mil.tron.commonapi.dto;

import mil.tron.commonapi.entity.Airman;
import mil.tron.commonapi.entity.Squadron;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SquadronTerseDtoTest {

    @Test
    void checkDtoFields() {
        Airman a = new Airman();
        Airman b = new Airman();
        Squadron s = new Squadron();

        // check just fields we had to manually make a setter for, plus check
        //  we can get/set to the parent class too...
        SquadronTerseDto dto = new SquadronTerseDto();
        dto.setChief(b);
        dto.setOperationsDirector(a);
        dto.setMembers(Set.of(a, b));
        dto.setParentOrganization(s);

        assertEquals(b.getId(), dto.getChief());
        assertEquals(a.getId(), dto.getOperationsDirector());
        assertEquals(Set.of(a.getId(), b.getId()), dto.getMembers());
        assertEquals(s.getId(), dto.getParentOrganization());
    }
}
