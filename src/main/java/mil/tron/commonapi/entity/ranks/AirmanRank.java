package mil.tron.commonapi.entity.ranks;

import java.util.HashMap;
import java.util.Map;


public enum AirmanRank {
    AB("AB"),
    AMN("AMN"),
    A1C("A1C"),
    SRA("SRA"),
    SSGT("SSGT"),
    TSGT("TSGT"),
    MSGT("MSGT"),
    SMGT("SMGT"),
    CMSGT("CMSGT"),
    CCMSGT("CCMSGT"),
    CMSAF("CMSAF"),
    SECOND_LT("2LT"),
    FIRST_LT("1LT"),
    CAPT("CAPT"),
    MAJ("MAJ"),
    LTCOL("LTCOL"),
    COL("COL"),
    BG("BG"),
    MG("MG"),
    LTG("LTG"),
    GEN("GEN"),
    CIV("CIV"),
    CTR("CTR"),
    SES("SES"),
    UNKNOWN("UNKNOWN");

    private static final Map<String, AirmanRank> BY_STRING = new HashMap<>();
    private static String RANK_LIST_STRING = "";

    static {
        for (AirmanRank rank: values()) {
            RANK_LIST_STRING += rank.toString() + ", ";
            BY_STRING.put(rank.toString(), rank);
        }
        // remove trailing comma of RANK_LIST_STRING
        RANK_LIST_STRING = RANK_LIST_STRING.substring(0, RANK_LIST_STRING.length() - 2);
    }

    private final String text;

    /**
     * @param text
     */
    AirmanRank(final String text) {
        this.text = text;
    }

    /* (non-Javadoc)
     * @see java.lang.Enum#toString()
     */
    @Override
    public String toString() {
        return text;
    }

    public static AirmanRank valueByString(String text) {
        return BY_STRING.get(text);
    }

    public static String getValueString() {
        return RANK_LIST_STRING;
    }

}
