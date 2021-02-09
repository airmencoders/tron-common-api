package mil.tron.commonapi.entity.ranks;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class AirmanRankTest {

    @Test
    void testToString() {
        AirmanRank colRank = AirmanRank.COL;
        assertEquals("COL", colRank.toString());
    }

    @Test
    void testValueByString() {
        AirmanRank majRank = AirmanRank.valueByString("MAJ");
        assertEquals(AirmanRank.MAJ, majRank);
    }

    @Test
    void testGetValueString() {
        String expectedString = Arrays.stream(AirmanRank.values())
                .map(rank -> rank.toString())
                .collect(Collectors.joining(", "));
        assertEquals(expectedString, AirmanRank.getValueString());
    }
}