package mil.tron.commonapi.entity;

import mil.tron.commonapi.exception.InvalidFieldValueException;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AirmanTests {

    @Test
    public void airmanObjTests() {
        Airman a = new Airman();
        a.setAfsc("17D");
        a.setPtDate(new Date(2020-1900, Calendar.AUGUST, 11));
        a.setEtsDate(new Date(2020-1900, Calendar.DECEMBER, 31));
        a.setDodid("1234567890");
        assertEquals("17D", a.getAfsc());
        assertEquals(new Date(2020-1900, Calendar.AUGUST, 11), a.getPtDate());
        assertEquals(new Date(2020-1900, Calendar.DECEMBER, 31), a.getEtsDate());
        assertEquals("1234567890", a.getDodid());
    }

    @Test
    public void badRankTest() {
        Airman a = new Airman();
        assertThrows(InvalidFieldValueException.class, () -> a.setRank("test"));
    }
}
