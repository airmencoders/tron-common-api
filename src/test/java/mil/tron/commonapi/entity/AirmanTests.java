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

//    @Test
//    public void badRankTest() {
//        Airman a = new Airman();
//        assertThrows(InvalidFieldValueException.class, () -> a.setRank("test"));
//    }

    @Test
    void testStringTrims() {
        Airman airman = new Airman();

        airman.setAfsc(" 92T1 ");
        airman.setDodid(" 0123456789 ");
        airman.setImds(" 00-20-3 ");
        airman.setUnit(" Test Unit ");
        airman.setWing(" Test Wing ");
        airman.setGp(" Test Group ");
        airman.setSquadron(" Test Squadron ");
        airman.setWc(" Test Wing Commander ");
        airman.setGo81(" Test Go81? ");
        airman.setDeros(" 2022-01-01 ");
        airman.setPhone(" (808)123-4567 ");
        airman.setAddress(" 1234 Test Street ");
        airman.setFltChief(" Test Flight Chief ");
        airman.setManNumber(" Test Man Number ");
        airman.setDutyTitle(" Test Duty Title ");
        airman.sanitizeEntity();
        assertEquals(airman.getAfsc(), "92T1");
        assertEquals(airman.getDodid(), "0123456789");
        assertEquals(airman.getImds(), "00-20-3");
        assertEquals(airman.getUnit(), "Test Unit");
        assertEquals(airman.getWing(), "Test Wing");
        assertEquals(airman.getGp(), "Test Group");
        assertEquals(airman.getSquadron(), "Test Squadron");
        assertEquals(airman.getWc(), "Test Wing Commander");
        assertEquals(airman.getGo81(), "Test Go81?");
        assertEquals(airman.getDeros(), "2022-01-01");
        assertEquals(airman.getPhone(), "(808)123-4567");
        assertEquals(airman.getAddress(), "1234 Test Street");
        assertEquals(airman.getFltChief(), "Test Flight Chief");
        assertEquals(airman.getManNumber(), "Test Man Number");
        assertEquals(airman.getDutyTitle(), "Test Duty Title");
    }
}
