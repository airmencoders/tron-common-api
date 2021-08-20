package mil.tron.commonapi.dto.annotation.helper;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PatchOp {
    ADD("add"),
    REMOVE("remove"),
    REPLACE("replace"),
    COPY("copy"),
    MOVE("move"),
    TEST("test");

    private final String text;

    /**
     * @param text
     */
    PatchOp(final String text) {
        this.text = text;
    }

    /* (non-Javadoc)
     * @see java.lang.Enum#toString()
     */
    @Override
    public String toString() {
        return text;
    }
    
    @JsonValue
    public String getText() {
    	return text;
    }
}