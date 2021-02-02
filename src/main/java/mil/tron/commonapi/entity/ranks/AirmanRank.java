package mil.tron.commonapi.entity.ranks;

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

}
